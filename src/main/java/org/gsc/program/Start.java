package org.gsc.program;

import lombok.extern.slf4j.Slf4j;
import org.gsc.common.app.Application;
import org.gsc.common.app.ApplicationImpl;
import org.gsc.config.Args;
import org.gsc.config.DefaultConfig;
import org.gsc.core.Constant;
import org.gsc.service.ProducerService;
import org.gsc.service.RpcApiService;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;


@Slf4j
public class Start {

  /**
   * Start the FullNode.
   */
  public static void main(String[] args) throws InterruptedException {
    logger.info("gsc node running.");

    logger.info("Full node running.");
    Args.setParam(args, Constant.TESTNET_CONF);
    Args cfgArgs = Args.getInstance();

    if (cfgArgs.isHelp()) {
      logger.info("Here is the help message.");
      return;
    }

    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.setAllowCircularReferences(false);
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(beanFactory);
    context.register(DefaultConfig.class);
    context.refresh();
    Application appT = context.getBean(ApplicationImpl.class);
    Args config =  context.getBean(Args.class);

    shutdown(appT);

    RpcApiService rpcApiService = context.getBean(RpcApiService.class);
    appT.addService(rpcApiService);
    if (config.isProd()) {
      appT.addService(new ProducerService(appT, context));
    }
    appT.initServices(config);
    appT.startServices();
    appT.startup();
    rpcApiService.blockUntilShutdown();

  }



  private static void shutdown(final Application app) {
  }
}

