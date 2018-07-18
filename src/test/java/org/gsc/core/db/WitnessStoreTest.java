package org.gsc.core.db;

import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.gsc.core.wrapper.WitnessWrapper;
import org.gsc.db.WitnessStore;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.gsc.common.utils.FileUtil;
import org.gsc.core.Constant;
import org.gsc.config.DefaultConfig;
import org.gsc.config.args.Args;

@Slf4j
public class WitnessStoreTest {

  private static final String dbPath = "output-witnessStore-test";
  private static AnnotationConfigApplicationContext context;
  WitnessStore witnessStore;

  static {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new AnnotationConfigApplicationContext(DefaultConfig.class);
  }

  @Before
  public void initDb() {
    this.witnessStore = context.getBean(WitnessStore.class);
  }

  @AfterClass
  public static void destroy() {
    Args.clearParam();
    FileUtil.deleteDir(new File(dbPath));
    context.destroy();
  }

  @Test
  public void putAndGetWitness() {
    WitnessWrapper witnessWrapper = new WitnessWrapper(ByteString.copyFromUtf8("100000000x"), 100L,
        "");

    this.witnessStore.put(witnessWrapper.getAddress().toByteArray(), witnessWrapper);
    WitnessWrapper witnessSource = this.witnessStore
        .get(ByteString.copyFromUtf8("100000000x").toByteArray());
    Assert.assertEquals(witnessWrapper.getAddress(), witnessSource.getAddress());
    Assert.assertEquals(witnessWrapper.getVoteCount(), witnessSource.getVoteCount());

    Assert.assertEquals(ByteString.copyFromUtf8("100000000x"), witnessSource.getAddress());
    Assert.assertEquals(100L, witnessSource.getVoteCount());

    witnessWrapper = new WitnessWrapper(ByteString.copyFromUtf8(""), 100L, "");

    this.witnessStore.put(witnessWrapper.getAddress().toByteArray(), witnessWrapper);
    witnessSource = this.witnessStore.get(ByteString.copyFromUtf8("").toByteArray());
    Assert.assertEquals(witnessWrapper.getAddress(), witnessSource.getAddress());
    Assert.assertEquals(witnessWrapper.getVoteCount(), witnessSource.getVoteCount());

    Assert.assertEquals(ByteString.copyFromUtf8(""), witnessSource.getAddress());
    Assert.assertEquals(100L, witnessSource.getVoteCount());
  }


}