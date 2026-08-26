package megane6.weplanet.domain.entity.enumfolder;

import java.util.Arrays;

public enum SettlementBank {
    KOOKMIN("004", "06", "국민은행", true),
    SHINHAN("088", "88", "신한은행", true),
    NONGHYEOP("011", "11", "농협은행", true),
    KAKAO_BANK("090", "90", "카카오뱅크", false),
    WOORI("020", "20", "우리은행", true),
    HANA("081", "81", "하나은행", true),
    TOSS_BANK("092", "92", "토스뱅크", false),
    BUSAN("032", "32", "부산은행", true),
    IM_BANK("031", "31", "iM뱅크", true);

    private final String financialCode;
    private final String tossPaymentsCode;
    private final String displayName;
    private final boolean tossVirtualAccountSupported;

    SettlementBank(
            String financialCode,
            String tossPaymentsCode,
            String displayName,
            boolean tossVirtualAccountSupported
    ) {
        this.financialCode = financialCode;
        this.tossPaymentsCode = tossPaymentsCode;
        this.displayName = displayName;
        this.tossVirtualAccountSupported = tossVirtualAccountSupported;
    }

    public String getFinancialCode() {
        return financialCode;
    }

    public String getTossPaymentsCode() {
        return tossPaymentsCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTossVirtualAccountSupported() {
        return tossVirtualAccountSupported;
    }

    public static SettlementBank fromFinancialCode(String financialCode) {
        return Arrays.stream(values())
                .filter(bank -> bank.financialCode.equals(financialCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 은행 코드입니다: " + financialCode));
    }
}
