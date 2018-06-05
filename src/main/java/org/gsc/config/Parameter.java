package org.gsc.config;

public interface Parameter {

  interface WalletConstant {
    public static final byte ADD_PRE_FIX_BYTE_MAINNET = (byte) 0xb0;   //b0 + address  ,b0 is version
    public static final String ADD_PRE_FIX_STRING_MAINNET = "b0";
    public static final int ADDRESS_SIZE = 42;
    public static final int BASE58CHECK_ADDRESS_SIZE = 35;

  }

  interface ChainConstant {

    int BLOCK_SIZE = 2_000_000;
    long TRANSFER_FEE = 0; // free
    long ASSET_ISSUE_FEE = 0;
    long WITNESS_PAY_PER_BLOCK = 0;
    double SOLIDIFIED_THRESHOLD = 0.7;
    int PRIVATE_KEY_LENGTH = 64;
    int MAX_ACTIVE_WITNESS_NUM = 31;
    double BLOCK_PRODUCED_TIME_OUT = 0.75;
    int TRXS_SIZE = 2_000_000; // < 2MiB
    int BLOCK_PRODUCED_INTERVAL = 3000; //ms,produce block period, must be divisible by 60. millisecond
    long CLOCK_MAX_DELAY = 3600 * 1000; //ms
    long BATCH_FETCH_RESPONSE_SIZE = 1000; //for each inventory message from peer, the max count of fetch inv message
    long WITNESS_STANDBY_ALLOWANCE = 230_400_000_000L;// 6 * 1200 * 32000000
    int WITNESS_STANDBY_LENGTH = 127;
    long TRANSACTION_MAX_BYTE_SIZE = 500 * 1_024L;
    long MAXIMUM_TIME_UNTIL_EXPIRATION = 24 * 60 * 60 * 1_000L; //one day
  }

}
