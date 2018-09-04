package org.gsc.program;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.gsc.core.wrapper.BlockWrapper;
import org.gsc.core.wrapper.TransactionWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;
import org.gsc.common.application.Application;
import org.gsc.common.application.ApplicationFactory;
import org.gsc.common.application.GSCApplicationContext;
import org.gsc.common.overlay.client.DatabaseGrpcClient;
import org.gsc.common.overlay.discover.DiscoverServer;
import org.gsc.common.overlay.discover.node.NodeManager;
import org.gsc.common.overlay.server.ChannelManager;
import org.gsc.core.Constant;
import org.gsc.core.wrapper.TransactionInfoWrapper;
import org.gsc.config.DefaultConfig;
import org.gsc.config.args.Args;
import org.gsc.db.Manager;
import org.gsc.core.exception.AccountResourceInsufficientException;
import org.gsc.core.exception.BadBlockException;
import org.gsc.core.exception.BadItemException;
import org.gsc.core.exception.BadNumberBlockException;
import org.gsc.core.exception.ContractExeException;
import org.gsc.core.exception.ContractValidateException;
import org.gsc.core.exception.DupTransactionException;
import org.gsc.core.exception.NonCommonBlockException;
import org.gsc.core.exception.ReceiptCheckErrException;
import org.gsc.core.exception.ReceiptException;
import org.gsc.core.exception.TaposException;
import org.gsc.core.exception.TooBigTransactionException;
import org.gsc.core.exception.TooBigTransactionResultException;
import org.gsc.core.exception.TransactionExpirationException;
import org.gsc.core.exception.TransactionTraceException;
import org.gsc.core.exception.UnLinkedBlockException;
import org.gsc.core.exception.UnsupportVMException;
import org.gsc.core.exception.ValidateScheduleException;
import org.gsc.core.exception.ValidateSignatureException;
import org.gsc.services.RpcApiService;
import org.gsc.services.http.solidity.SolidityNodeHttpApiService;
import org.gsc.protos.Protocol.Block;
import org.gsc.protos.Protocol.DynamicProperties;

@Slf4j
public class SolidityNode {

  private DatabaseGrpcClient databaseGrpcClient;
  private Manager dbManager;

  private ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor();

  public void setDbManager(Manager dbManager) {
    this.dbManager = dbManager;
  }

  private void initGrpcClient(String addr) {
    try {
      databaseGrpcClient = new DatabaseGrpcClient(addr);
    } catch (Exception e) {
      logger.error("Failed to create database grpc client {}", addr);
      System.exit(0);
    }
  }

  private void shutdownGrpcClient() {
    if (databaseGrpcClient != null) {
      databaseGrpcClient.shutdown();
    }
  }

  private void syncLoop(Args args) {
//    while (true) {
//      try {
//        initGrpcClient(args.getTrustNodeAddr());
//        syncSolidityBlock();
//        shutdownGrpcClient();
//      } catch (Exception e) {
//        logger.error("Error in sync solidity block " + e.getMessage(), e);
//      }
//      try {
//        Thread.sleep(5000);
//      } catch (InterruptedException e) {
//        Thread.currentThread().interrupt();
//        e.printStackTrace();
//      }
//    }
  }

  private void syncSolidityBlock() throws BadBlockException {
    DynamicProperties remoteDynamicProperties = databaseGrpcClient.getDynamicProperties();
    long remoteLastSolidityBlockNum = remoteDynamicProperties.getLastSolidityBlockNum();
    while (true) {

//      try {
//        Thread.sleep(10000);
//      } catch (Exception e) {
//
//      }
      long lastSolidityBlockNum = dbManager.getDynamicPropertiesStore()
          .getLatestSolidifiedBlockNum();
      logger.info("sync solidity block, lastSolidityBlockNum:{}, remoteLastSolidityBlockNum:{}",
          lastSolidityBlockNum, remoteLastSolidityBlockNum);
      if (lastSolidityBlockNum < remoteLastSolidityBlockNum) {
        Block block = databaseGrpcClient.getBlock(lastSolidityBlockNum + 1);
        try {
          BlockWrapper blockWrapper = new BlockWrapper(block);
          dbManager.pushBlock(blockWrapper);
          for (TransactionWrapper trx : blockWrapper.getTransactions()) {
            TransactionInfoWrapper ret;
            try {
              ret = dbManager.getTransactionHistoryStore().get(trx.getTransactionId().getBytes());
            } catch (BadItemException ex) {
              logger.warn("", ex);
              continue;
            }
            ret.setBlockNumber(blockWrapper.getNum());
            ret.setBlockTimeStamp(blockWrapper.getTimeStamp());
            dbManager.getTransactionHistoryStore().put(trx.getTransactionId().getBytes(), ret);
          }
          dbManager.getDynamicPropertiesStore()
              .saveLatestSolidifiedBlockNum(lastSolidityBlockNum + 1);
        } catch (AccountResourceInsufficientException e) {
          throw new BadBlockException("validate AccountResource exception");
        } catch (ValidateScheduleException e) {
          throw new BadBlockException("validate schedule exception");
        } catch (ValidateSignatureException e) {
          throw new BadBlockException("validate signature exception");
        } catch (ContractValidateException e) {
          throw new BadBlockException("ContractValidate exception");
        } catch (ContractExeException | UnLinkedBlockException e) {
          throw new BadBlockException("Contract Execute exception");
        } catch (TaposException e) {
          throw new BadBlockException("tapos exception");
        } catch (DupTransactionException e) {
          throw new BadBlockException("dup exception");
        } catch (TooBigTransactionException e) {
          throw new BadBlockException("too big exception");
        } catch (TooBigTransactionResultException e) {
          throw new BadBlockException("too big exception result");
        } catch (TransactionExpirationException e) {
          throw new BadBlockException("expiration exception");
        } catch (BadNumberBlockException e) {
          throw new BadBlockException("bad number exception");
        } catch (ReceiptException e) {
          throw new BadBlockException("Receipt exception");
        } catch (NonCommonBlockException e) {
          throw new BadBlockException("non common exception");
        } catch (TransactionTraceException e) {
          throw new BadBlockException("TransactionTrace Exception");
        } catch (ReceiptCheckErrException e) {
          throw new BadBlockException("OutOfSlotTime Exception");
        } catch (UnsupportVMException e) {
          throw new BadBlockException(e.getMessage());
        }

      } else {
        break;
      }
    }
    logger.info("Sync with trust node completed!!!");
  }

  private void start(Args cfgArgs) {
    syncExecutor.scheduleWithFixedDelay(() -> {
      try {
        initGrpcClient(cfgArgs.getTrustNodeAddr());
        syncSolidityBlock();
        shutdownGrpcClient();
      } catch (Throwable t) {
        logger.error("Error in sync solidity block " + t.getMessage(), t);
      }
    }, 5000, 5000, TimeUnit.MILLISECONDS);
    //new Thread(() -> syncLoop(cfgArgs), logger.getName()).start();
  }

  /**
   * Start the SolidityNode.
   */
  public static void main(String[] args) throws InterruptedException {
    logger.info("Solidity node running.");
    Args.setParam(args, Constant.TESTNET_CONF);
    Args cfgArgs = Args.getInstance();

    if (StringUtils.isEmpty(cfgArgs.getTrustNodeAddr())) {
      logger.error("Trust node not set.");
      return;
    }
    cfgArgs.setSolidityNode(true);

    ApplicationContext context = new GSCApplicationContext(DefaultConfig.class);

    if (cfgArgs.isHelp()) {
      logger.info("Here is the help message.");
      return;
    }
    Application appT = ApplicationFactory.create(context);
    FullNode.shutdown(appT);

    //appT.init(cfgArgs);
    RpcApiService rpcApiService = context.getBean(RpcApiService.class);
    appT.addService(rpcApiService);
    //http
    SolidityNodeHttpApiService httpApiService = context.getBean(SolidityNodeHttpApiService.class);
    appT.addService(httpApiService);

    appT.initServices(cfgArgs);
    appT.startServices();
//    appT.startup();

    //Disable peer discovery for solidity node
    DiscoverServer discoverServer = context.getBean(DiscoverServer.class);
    discoverServer.close();
    ChannelManager channelManager = context.getBean(ChannelManager.class);
    channelManager.close();
    NodeManager nodeManager = context.getBean(NodeManager.class);
    nodeManager.close();

    SolidityNode node = new SolidityNode();
    node.setDbManager(appT.getDbManager());
    node.start(cfgArgs);

    rpcApiService.blockUntilShutdown();
  }
}
