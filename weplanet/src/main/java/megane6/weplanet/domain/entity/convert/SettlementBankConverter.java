package megane6.weplanet.domain.entity.convert;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;

@Converter
public class SettlementBankConverter implements AttributeConverter<SettlementBank, String> {

    @Override
    public String convertToDatabaseColumn(SettlementBank bank) {
        return bank == null ? null : bank.getFinancialCode();
    }

    @Override
    public SettlementBank convertToEntityAttribute(String financialCode) {
        return financialCode == null ? null : SettlementBank.fromFinancialCode(financialCode);
    }
}
