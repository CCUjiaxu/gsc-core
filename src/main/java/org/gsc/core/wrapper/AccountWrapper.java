package org.gsc.core.wrapper;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.gsc.common.utils.ByteArray;
import org.gsc.protos.Protocol.Account;
import org.gsc.protos.Protocol.AccountType;

@Slf4j
public class AccountWrapper implements StoreWrapper<Account>, Comparable<AccountWrapper> {

  private Account account;

  @Override
  public int compareTo(AccountWrapper o) {
    return 0;
  }

  @Override
  public byte[] getData() {
    return new byte[0];
  }

  @Override
  public Account getInstance() {
    return null;
  }

  public AccountWrapper(byte[] data) {
    try {
      this.account = Account.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  /**
   * get account from address.
   */
  public AccountWrapper(ByteString address,
      AccountType accountType, long createTime) {
    this.account = Account.newBuilder()
        .setType(accountType)
        .setAddress(address)
        .setCreateTime(createTime)
        .build();
  }

  public long getBalance() {
    return this.account.getBalance();
  }

  public void setBalance(long balance) {
    this.account = this.account.toBuilder().setBalance(balance).build();
  }

  public ByteString getAddress() {
    return this.account.getAddress();
  }


  public long getPower() {
    long p = 0;
    //long now = Time.getCurrentMillis();
    for (int i = 0; i < account.getFrozenCount(); ++i) {
      p += account.getFrozen(i).getFrozenBalance();
    }
    return p;
  }

  /**
   * reduce asset amount.
   */
  public boolean reduceTokenAmount(ByteString name, long amount) {
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(name.toByteArray());
    Long currentAmount = assetMap.get(nameKey);
    if (amount > 0 && null != currentAmount && amount <= currentAmount) {
      this.account = this.account.toBuilder()
          .putAsset(nameKey, Math.subtractExact(currentAmount, amount)).build();
      return true;
    }

    return false;
  }

  /**
   * add asset amount.
   */
  public boolean addTokenAmount(ByteString name, long amount) {
    Map<String, Long> assetMap = this.account.getAssetMap();
    String nameKey = ByteArray.toStr(name.toByteArray());
    Long currentAmount = assetMap.get(nameKey);
    if (currentAmount == null) {
      currentAmount = 0L;
    }
    this.account = this.account.toBuilder().putAsset(nameKey, Math.addExact(currentAmount, amount))
        .build();
    return true;
  }

  public void setAllowance(long allowance) {
    this.account = this.account.toBuilder().setAllowance(allowance).build();
  }

  public long getAllowance() {
    return getInstance().getAllowance();
  }

  public byte[] createDbKey() {
    return getAddress().toByteArray();
  }

}
