package com.practice.spring_ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class PersonalFinanceTools {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 10, 6);
    private static final String CURRENCY = "USD";
    private static final BigDecimal BALANCE = new BigDecimal("2450.00");

    private static final List<Expense> EXPENSES = List.of(
            new Expense(LocalDate.of(2026, 10, 1), "Housing", "Rent", new BigDecimal("1200.00")),
            new Expense(LocalDate.of(2026, 10, 2), "Groceries", "Supermarket", new BigDecimal("85.50")),
            new Expense(LocalDate.of(2026, 10, 3), "Transport", "Bus pass", new BigDecimal("24.00")),
            new Expense(LocalDate.of(2026, 10, 4), "Dining", "Restaurant", new BigDecimal("42.75")),
            new Expense(LocalDate.of(2026, 10, 5), "Groceries", "Supermarket", new BigDecimal("67.25"))
    );

    @Tool(name = "getCurrentBalance", description = "Get the sample account balance in USD as of October 6, 2026.")
    public Balance getCurrentBalance() {
        log.info("Finance tool invoked: getCurrentBalance");
        return new Balance(SNAPSHOT_DATE, CURRENCY, BALANCE);
    }

    @Tool(name = "getExpenses", description = "Get all sample expenses from October 1 through October 6, 2026, with dates, categories, descriptions and amounts in USD. Use these records for questions about spending, totals, or spending by category.")
    public List<Expense> getExpenses() {
        log.info("Finance tool invoked: getExpenses");
        return EXPENSES;
    }

    public record Balance(LocalDate asOf, String currency, BigDecimal amount) { }

    public record Expense(LocalDate date, String category, String description, BigDecimal amount) { }
}
