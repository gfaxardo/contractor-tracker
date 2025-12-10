package com.yego.contractortracker.dto;

import com.yego.contractortracker.entity.YangoTransaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YangoTransactionGroup {
    private String driverNameFromComment;
    private List<YangoTransaction> transactions;
    private Integer count;
    
    public YangoTransactionGroup(String driverNameFromComment, List<YangoTransaction> transactions) {
        this.driverNameFromComment = driverNameFromComment;
        this.transactions = transactions;
        this.count = transactions != null ? transactions.size() : 0;
    }
}











