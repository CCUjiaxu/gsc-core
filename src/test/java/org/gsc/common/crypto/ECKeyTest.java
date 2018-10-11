package org.gsc.common.crypto;

import static org.junit.Assert.assertEquals;

import lombok.extern.slf4j.Slf4j;
import org.gsc.common.utils.Utils;
import org.gsc.crypto.ECKey;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.gsc.common.utils.ByteArray;
import org.gsc.core.Wallet;

@Slf4j
public class ECKeyTest {

  @Test
  public void testGeClientTestEcKey() {
    final ECKey key = ECKey.fromPrivate(
        Hex.decode("1cd5a70741c6e583d2dd3c5f17231e608eb1e52437210d948c5085e141c2d830"));

    assertEquals(Wallet.getAddressPreFixString() + "125b6c87b3d67114b3873977888c34582f27bbb0",
        ByteArray.toHexString(key.getAddress()));

    ECKey ecKey = new ECKey(Utils.getRandom());
    logger.info(ByteArray.toHexString(ecKey
            .getAddress()));

  }
}