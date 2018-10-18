package org.gsc.core.db.api;

import static com.googlecode.cqengine.query.QueryFactory.equal;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.googlecode.cqengine.resultset.ResultSet;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.gsc.core.wrapper.*;
import org.gsc.db.api.IndexHelper;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.gsc.common.utils.ByteArray;
import org.gsc.common.utils.FileUtil;
import org.gsc.core.wrapper.BlockWrapper;
import org.gsc.config.DefaultConfig;
import org.gsc.config.args.Args;
import org.gsc.db.Manager;
import org.gsc.db.api.index.AccountIndex;
import org.gsc.db.api.index.Index;
import org.gsc.protos.Contract.AssetIssueContract;
import org.gsc.protos.Protocol.Account;
import org.gsc.protos.Protocol.Block;
import org.gsc.protos.Protocol.BlockHeader;
import org.gsc.protos.Protocol.BlockHeader.raw;
import org.gsc.protos.Protocol.Transaction;
import org.gsc.protos.Protocol.Witness;

@Slf4j
public class IndexHelperTest {

  private static Manager dbManager;
  private static IndexHelper indexHelper;
  private static AnnotationConfigApplicationContext context;
  private static String dbPath = "output_IndexHelper_test";

  static {
    Args.setParam(new String[]{"-d", dbPath, "-w"}, "config-test-index.conf");
    Args.getInstance().setSolidityNode(true);
    context = new AnnotationConfigApplicationContext(DefaultConfig.class);
  }

  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    AccountWrapper accountWrapper =
        new AccountWrapper(
            Account.newBuilder()
                .setAddress(ByteString.copyFrom(ByteArray.fromHexString("121212abc")))
                .build());
    dbManager.getAccountStore().put(ByteArray.fromHexString("121212abc"), accountWrapper);
    BlockWrapper blockWrapper =
        new BlockWrapper(
            Block.newBuilder()
                .setBlockHeader(
                    BlockHeader.newBuilder()
                        .setRawData(raw.newBuilder().setNumber(4).build())
                        .build())
                .build());
    dbManager.getBlockStore().put(blockWrapper.getBlockId().getBytes(), blockWrapper);
    WitnessWrapper witnessWrapper =
        new WitnessWrapper(
            Witness.newBuilder()
                .setAddress(ByteString.copyFrom(ByteArray.fromHexString("121212abc")))
                .build());
    dbManager.getWitnessStore().put(ByteArray.fromHexString("121212abc"), witnessWrapper);
    TransactionWrapper transactionWrapper =
        new TransactionWrapper(
            Transaction.newBuilder()
                .setRawData(
                    Transaction.raw
                        .newBuilder()
                        .setData(ByteString.copyFrom("i am trans".getBytes()))
                        .build())
                .build());
    dbManager
        .getTransactionStore()
        .put(transactionWrapper.getTransactionId().getBytes(), transactionWrapper);
    AssetIssueWrapper assetIssueWrapper =
        new AssetIssueWrapper(
            AssetIssueContract.newBuilder()
                .setName(ByteString.copyFrom("assetIssueName".getBytes()))
                .setNum(12581)
                .build());
    dbManager.getAssetIssueStore().put("assetIssueName".getBytes(), assetIssueWrapper);
    indexHelper = context.getBean(IndexHelper.class);
  }

  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    FileUtil.deleteDir(new File(dbPath));
    context.destroy();
  }

  @Ignore
  @Test
  public void initTest() {

    int sizeOfAccount = getIndexSizeOfAccount();
    Assert.assertEquals("account index num", 1, sizeOfAccount);

    int sizeOfBlock = getIndexSizeOfBlock();
    Assert.assertEquals("block index num", 2, sizeOfBlock);

    int sizeOfWitness = getIndexSizeOfWitness();
    Assert.assertEquals("witness index num", 1, sizeOfWitness);

    int sizeOfTransaction = getIndexSizeOfTransaction();
    Assert.assertEquals("transaction index num", 1, sizeOfTransaction);

    int sizeOfAssetIssue = getIndexSizeOfAssetIssue();
    Assert.assertEquals("assetIssue index num", 1, sizeOfAssetIssue);
  }

  @Ignore
  @Test
  public void addAndRemoveAccount() {
    AccountWrapper accountWrapper =
        new AccountWrapper(
            Account.newBuilder()
                .setAddress(ByteString.copyFrom(ByteArray.fromHexString("232323abc")))
                .build());
    dbManager.getAccountStore().put(accountWrapper.createDbKey(), accountWrapper);
    indexHelper.add(accountWrapper.getInstance());
    int size = getIndexSizeOfAccount();
    Assert.assertEquals("account index add", 2, size);
    indexHelper.remove(accountWrapper.getInstance());
    size = getIndexSizeOfAccount();
    Assert.assertEquals("account index remove", 1, size);
  }

  private int getIndexSizeOfAccount() {
    Index.Iface<Account> accountIndex = indexHelper.getAccountIndex();
    ImmutableList<Account> accountImmutableList = ImmutableList.copyOf(accountIndex);
    return accountImmutableList.size();
  }

  @Ignore
  @Test
  public void addAndRemoveBlock() {
    BlockWrapper blockWrapper =
        new BlockWrapper(
            Block.newBuilder()
                .setBlockHeader(
                    BlockHeader.newBuilder()
                        .setRawData(raw.newBuilder().setNumber(6).build())
                        .build())
                .build());
    dbManager.getBlockStore().put(blockWrapper.getBlockId().getBytes(), blockWrapper);
    indexHelper.add(blockWrapper.getInstance());
    int size = getIndexSizeOfBlock();
    Assert.assertEquals("block index add", 3, size);
    indexHelper.remove(blockWrapper.getInstance());
    size = getIndexSizeOfBlock();
    Assert.assertEquals("block index remove", 2, size);
  }

  private int getIndexSizeOfBlock() {
    Index.Iface<Block> blockIndex = indexHelper.getBlockIndex();
    ImmutableList<Block> accountImmutableList = ImmutableList.copyOf(blockIndex);
    return accountImmutableList.size();
  }

  @Ignore
  @Test
  public void addAndRemoveWitness() {
    WitnessWrapper witnessWrapper =
        new WitnessWrapper(
            Witness.newBuilder()
                .setAddress(ByteString.copyFrom(ByteArray.fromHexString("343434abc")))
                .build());
    dbManager.getWitnessStore().put(witnessWrapper.createDbKey(), witnessWrapper);
    indexHelper.add(witnessWrapper.getInstance());
    int size = getIndexSizeOfWitness();
    Assert.assertEquals("witness index add", 2, size);
    indexHelper.remove(witnessWrapper.getInstance());
    size = getIndexSizeOfWitness();
    Assert.assertEquals("witness index remove", 1, size);
  }

  private int getIndexSizeOfWitness() {
    Index.Iface<Witness> witnessIndex = indexHelper.getWitnessIndex();
    ImmutableList<Witness> witnessImmutableList = ImmutableList.copyOf(witnessIndex);
    return witnessImmutableList.size();
  }

  @Test
  public void addAndRemoveTransaction() {
    TransactionWrapper transactionWrapper =
        new TransactionWrapper(
            Transaction.newBuilder()
                .setRawData(
                    Transaction.raw
                        .newBuilder()
                        .setData(ByteString.copyFrom("i am trans".getBytes()))
                        .build())
                .build());
    dbManager.getTransactionStore()
        .put(transactionWrapper.getTransactionId().getBytes(), transactionWrapper);
    indexHelper.add(transactionWrapper.getInstance());
    int size = getIndexSizeOfTransaction();
    Assert.assertEquals("account index add", 1, size);
    indexHelper.remove(transactionWrapper.getInstance());
    size = getIndexSizeOfTransaction();
    Assert.assertEquals("account index remove", 0, size);
  }

  private int getIndexSizeOfTransaction() {
    Index.Iface<Transaction> transactionIndex = indexHelper.getTransactionIndex();
    ImmutableList<Transaction> accountImmutableList = ImmutableList.copyOf(transactionIndex);
    return accountImmutableList.size();
  }

  @Ignore
  @Test
  public void addAndRemoveAssetIssue() {
    AssetIssueWrapper assetIssueWrapper =
        new AssetIssueWrapper(
            AssetIssueContract.newBuilder()
                .setName(ByteString.copyFrom("assetIssueName".getBytes()))
                .setNum(12581)
                .build());
    dbManager.getAssetIssueStore()
        .put(assetIssueWrapper.getName().toByteArray(), assetIssueWrapper);
    indexHelper.add(assetIssueWrapper.getInstance());
    int size = getIndexSizeOfAssetIssue();
    Assert.assertEquals("account index add", 1, size);
    indexHelper.remove(assetIssueWrapper.getInstance());
    size = getIndexSizeOfAssetIssue();
    Assert.assertEquals("account index remove", 0, size);
  }

  private int getIndexSizeOfAssetIssue() {
    Index.Iface<AssetIssueContract> assetIssueContractIndex =
        indexHelper.getAssetIssueIndex();
    ImmutableList<AssetIssueContract> accountImmutableList =
        ImmutableList.copyOf(assetIssueContractIndex);
    return accountImmutableList.size();
  }

  @Ignore
  @Test
  public void update() {
    /*
     * account1, account2, account3 has the same address, so in index there are only one instance.
     */
    // account1
    Account account1 = Account.newBuilder()
        .setAddress(ByteString.copyFrom("update123".getBytes()))
        .setBalance(123)
        .build();
    dbManager.getAccountStore()
        .put(account1.getAddress().toByteArray(), new AccountWrapper(account1));
    indexHelper.update(account1);
    ResultSet<Account> resultSet = indexHelper.getAccountIndex()
        .retrieve(equal(AccountIndex.Account_ADDRESS,
            ByteArray.toHexString(account1.getAddress().toByteArray())));
    Assert.assertEquals(1, resultSet.size());
    Assert.assertEquals(123, resultSet.uniqueResult().getBalance());
    logger.info("account1 balance: " + resultSet.uniqueResult().getBalance());

    // account2
    Account account2 = Account.newBuilder()
        .setAddress(ByteString.copyFrom("update123".getBytes()))
        .setBalance(456)
        .build();
    dbManager.getAccountStore()
        .put(account1.getAddress().toByteArray(), new AccountWrapper(account2));
    indexHelper.update(account2);
    resultSet = indexHelper.getAccountIndex()
        .retrieve(equal(AccountIndex.Account_ADDRESS,
            ByteArray.toHexString(account1.getAddress().toByteArray())));
    Assert.assertEquals(1, resultSet.size());
    Assert.assertEquals(456, resultSet.uniqueResult().getBalance());
    logger.info("account2 balance: " + resultSet.uniqueResult().getBalance());

    // account3
    Account account3 = Account.newBuilder()
        .setAddress(ByteString.copyFrom("update123".getBytes()))
        .setBalance(789)
        .build();
    dbManager.getAccountStore()
        .put(account1.getAddress().toByteArray(), new AccountWrapper(account3));
    indexHelper.update(account3);
    resultSet = indexHelper.getAccountIndex()
        .retrieve(equal(AccountIndex.Account_ADDRESS,
            ByteArray.toHexString(account1.getAddress().toByteArray())));
    Assert.assertEquals(1, resultSet.size());
    Assert.assertEquals(789, resultSet.uniqueResult().getBalance());
    logger.info("account3 balance: " + resultSet.uniqueResult().getBalance());

    // del account
    indexHelper.remove(account3);
  }
}
