package org.gsc.core.operator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.gsc.common.utils.StringUtil;
import org.gsc.core.exception.ContractExeException;
import org.gsc.core.exception.ContractValidateException;
import org.gsc.core.Wallet;
import org.gsc.core.wrapper.AccountCapsule;
import org.gsc.core.wrapper.TransactionResultCapsule;
import org.gsc.db.Manager;
import org.gsc.protos.Contract.AccountCreateContract;
import org.gsc.protos.Protocol.Transaction.Result.code;

@Slf4j
public class CreateAccountOperator extends AbstractOperator {

  CreateAccountOperator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret)
      throws ContractExeException {
    long fee = calcFee();
    try {
      AccountCreateContract accountCreateContract = contract.unpack(AccountCreateContract.class);
      AccountCapsule accountCapsule = new AccountCapsule(accountCreateContract,
          dbManager.getHeadBlockTimeStamp());
      dbManager.getAccountStore()
          .put(accountCreateContract.getAccountAddress().toByteArray(), accountCapsule);
      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!contract.is(AccountCreateContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [AccountCreateContract],real type[" + contract
              .getClass() + "]");
    }
    final AccountCreateContract contract;
    try {
      contract = this.contract.unpack(AccountCreateContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
//    if (contract.getAccountName().isEmpty()) {
//      throw new ContractValidateException("AccountName is null");
//    }
    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid ownerAddress");
    }

    AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          "Account[" + readableOwnerAddress + "] not exists");
    }

    byte[] accountAddress = contract.getAccountAddress().toByteArray();
    if (!Wallet.addressValid(accountAddress)) {
      throw new ContractValidateException("Invalid account address");
    }

//    if (contract.getType() == null) {
//      throw new ContractValidateException("Type is null");
//    }

    if (dbManager.getAccountStore().has(accountAddress)) {
      throw new ContractValidateException("Account has existed");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(AccountCreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
