package org.gsc.common.utils;


import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.gsc.core.wrapper.TransactionWrapper;
import org.gsc.protos.Contract.TransferContract;
import org.gsc.protos.Protocol.Transaction;
import org.gsc.protos.Protocol.Transaction.Contract;

@Slf4j
public class TransactionUtil {

  public static Transaction newGenesisTransaction(byte[] key, long value)
      throws IllegalArgumentException {

    if (!AddressUtil.addressValid(key)) {
      throw new IllegalArgumentException("Invalidate address");
    }
    TransferContract transferContract = TransferContract.newBuilder()
        .setAmount(value)
        .setOwnerAddress(ByteString.copyFrom("0x000000000000000000000".getBytes()))
        .setToAddress(ByteString.copyFrom(key))
        .build();

    return new TransactionWrapper(transferContract,
        Contract.ContractType.TransferContract).getInstance();
  }

  /**
   * checkBalance.
   */
  private static boolean checkBalance(long totalBalance, long totalSpent) {
    return totalBalance == totalSpent;
  }

  public static boolean validAccountName(byte[] accountName) {
    if (ArrayUtils.isEmpty(accountName)) {
      return false;
    }

    if (accountName.length < 8) {
      return false;
    }

    if (accountName.length > 32) {
      return false;
    }
    // b must read able.
    for (byte b : accountName) {
      if (b < 0x21) {
        return false; // 0x21 = '!'
      }
      if (b > 0x7E) {
        return false; // 0x7E = '~'
      }
    }
    return true;
  }

  public static boolean validAssetName(byte[] assetName) {
    if (ArrayUtils.isEmpty(assetName)) {
      return false;
    }
    if (assetName.length > 32) {
      return false;
    }
    // b must read able.
    for (byte b : assetName) {
      if (b < 0x21) {
        return false; // 0x21 = '!'
      }
      if (b > 0x7E) {
        return false; // 0x7E = '~'
      }
    }
    return true;
  }

  public static boolean validTokenAbbrName(byte[] abbrName) {
    if (ArrayUtils.isEmpty(abbrName)) {
      return false;
    }
    if (abbrName.length > 5) {
      return false;
    }
    // b must read able.
    for (byte b : abbrName) {
      if (b < 0x21) {
        return false; // 0x21 = '!'
      }
      if (b > 0x7E) {
        return false; // 0x7E = '~'
      }
    }
    return true;
  }


  public static boolean validAssetDescription(byte[] description) {
    if (ArrayUtils.isEmpty(description)) {
      return true;   //description can empty
    }
    if (description.length > 200) {
      return false;
    }
    // other rules.
    return true;
  }

  public static boolean validUrl(byte[] url) {
    if (ArrayUtils.isEmpty(url)) {
      return false;
    }
    if (url.length > 256) {
      return false;
    }
    // other rules.
    return true;
  }
  /**
   * Get sender.
   */
 /* public static byte[] getSender(Transaction tx) {
    byte[] pubKey = tx.getRawData().getVin(0).getRawData().getPubKey().toByteArray();
    return ECKey.computeAddress(pubKey);
  } */

}
