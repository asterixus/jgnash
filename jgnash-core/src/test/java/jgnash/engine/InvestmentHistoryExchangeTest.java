package jgnash.engine;

import java.io.File;
import java.math.BigDecimal;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static jgnash.engine.InvestmentTransactionTest.createTransactionEntry;
import static jgnash.engine.TransactionFactory.generateBuyXTransaction;
import static org.junit.Assert.*;

/**
  * Unit test for investment history and exchange rate combinations
  *
  * @author Craig Cavanaugh
  */
 public class InvestmentHistoryExchangeTest {

     private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

     private String database;

     private Engine e;

     private Account expenseAccount;

     private Account usdBankAccount;

    private Account investAccount;

     private SecurityNode securityNode;

     private CurrencyNode usdCurrency;

     private CurrencyNode cadCurrency;

     private static final char[] PASSWORD = new char[]{};


     @Test
     public void testExchangeRate() {
         assertEquals(new BigDecimal("0.5"), usdCurrency.getExchangeRate(cadCurrency));
         assertEquals(new BigDecimal("2"), cadCurrency.getExchangeRate(usdCurrency));
     }

     private Date getDate(final String date) {
         return SIMPLE_DATE_FORMAT.parse(date, new ParsePosition(0));
     }

     @Test
     public void testHistorySearch() {

         final SecurityHistoryNode old = new SecurityHistoryNode();
         old.setDate(getDate("2014-06-26"));
         old.setPrice(new BigDecimal("500.00"));
         assertTrue(e.addSecurityHistory(securityNode, old));

         final SecurityHistoryNode today = new SecurityHistoryNode();
         today.setDate(getDate("2014-06-27"));
         today.setPrice(new BigDecimal("501.00"));
         assertTrue(e.addSecurityHistory(securityNode, today));

         final SecurityHistoryNode future = new SecurityHistoryNode();
         future.setDate(getDate("2014-06-28"));
         future.setPrice(new BigDecimal("502.00"));
         assertTrue(e.addSecurityHistory(securityNode, future));

         Optional<SecurityHistoryNode> search = securityNode.getClosestHistoryNode(getDate("2014-06-26"));
         assertEquals(old, search.get());

         search = securityNode.getClosestHistoryNode(getDate("2014-06-27"));
         assertEquals(today, search.get());

         search = securityNode.getClosestHistoryNode(getDate("2014-06-28"));
         assertEquals(future, search.get());

         // postdate closest search, should return null
         search = securityNode.getClosestHistoryNode(getDate("2014-06-29"));
         assertEquals(future, search.get());

         // predate closest search, should return null
         search = securityNode.getClosestHistoryNode(getDate("2014-06-25"));
         assertFalse(search.isPresent());

         // predate exact match, should turn null;
         search = securityNode.getHistoryNode(getDate("2014-06-25"));
         assertFalse(search.isPresent());

         // postdate exact match, should turn null;
         search = securityNode.getHistoryNode(getDate("2014-06-29"));
         assertFalse(search.isPresent());

         // exact match, should match
         search = securityNode.getHistoryNode(getDate("2014-06-27"));
         assertEquals(today, search.get());

         BigDecimal price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, usdCurrency, getDate("2014-06-29"));
         assertEquals(new BigDecimal("502.00"), price);

         price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, usdCurrency, getDate("2014-06-28"));
         assertEquals(new BigDecimal("502.00"), price);

         price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, usdCurrency, getDate("2014-06-27"));
         assertEquals(new BigDecimal("501.00"), price);

         price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, usdCurrency, getDate("2014-06-26"));
         assertEquals(new BigDecimal("500.00"), price);

         price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, usdCurrency, getDate("2014-06-25"));
         assertEquals(BigDecimal.ZERO, price);

         price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, cadCurrency, getDate("2014-06-25"));
         assertTrue(price.compareTo(BigDecimal.ZERO) == 0);

         price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, cadCurrency, getDate("2014-06-26"));
         assertTrue(price.compareTo(new BigDecimal("250.00")) == 0);

         price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, cadCurrency, getDate("2014-06-27"));
         assertTrue(price.compareTo(new BigDecimal("250.50")) == 0);

         price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, cadCurrency, getDate("2014-06-28"));
         assertTrue(price.compareTo(new BigDecimal("251.00")) == 0);

         price = Engine.getMarketPrice(Collections.<Transaction>emptyList(), securityNode, cadCurrency, getDate("2014-06-29"));
         assertTrue(price.compareTo(new BigDecimal("251.00")) == 0);

         /// Test with a transaction for history precedence ///

         List<TransactionEntry> fees = new ArrayList<>();
         fees.add(createTransactionEntry(investAccount, expenseAccount, new BigDecimal("20.00"), "Fees", TransactionTag.INVESTMENT_FEE));

         // Buying shares
         Transaction it = generateBuyXTransaction(usdBankAccount, investAccount, securityNode, new BigDecimal("501.34"), new BigDecimal("125"), BigDecimal.ONE, getDate("2014-06-27"), "Buy shares", fees);

         assertTrue(e.addTransaction(it));

         price = Engine.getMarketPrice(investAccount.getSortedTransactionList(), securityNode, usdCurrency, getDate("2014-06-27"));
         assertEquals(new BigDecimal("501.00"), price);

         /// Test a transaction after any known security history ///

         fees.clear();
         fees.add(createTransactionEntry(investAccount, expenseAccount, new BigDecimal("20.00"), "Fees", TransactionTag.INVESTMENT_FEE));
         it = generateBuyXTransaction(usdBankAccount, investAccount, securityNode, new BigDecimal("501.34"), new BigDecimal("125"), BigDecimal.ONE, getDate("2014-06-29"), "Buy shares", fees);

         assertTrue(e.addTransaction(it));


         price = Engine.getMarketPrice(investAccount.getSortedTransactionList(), securityNode, usdCurrency, getDate("2014-06-29"));
         assertEquals(new BigDecimal("501.34"), price);

         price = Engine.getMarketPrice(investAccount.getSortedTransactionList(), securityNode, usdCurrency, getDate("2014-06-30"));
         assertEquals(new BigDecimal("501.34"), price);

         /// Test a transaction after any known security history and between a newer ///

         fees.clear();
         fees.add(createTransactionEntry(investAccount, expenseAccount, new BigDecimal("20.00"), "Fees", TransactionTag.INVESTMENT_FEE));
         it = generateBuyXTransaction(usdBankAccount, investAccount, securityNode, new BigDecimal("502.34"), new BigDecimal("125"), BigDecimal.ONE, getDate("2014-07-01"), "Buy shares", fees);

         assertTrue(e.addTransaction(it));

         price = Engine.getMarketPrice(investAccount.getSortedTransactionList(), securityNode, usdCurrency, getDate("2014-07-01"));
         assertFalse(securityNode.getHistoryNode(getDate("2014-07-01")).isPresent());
         assertEquals(new BigDecimal("502.34"), price);

         price = Engine.getMarketPrice(investAccount.getSortedTransactionList(), securityNode, usdCurrency, getDate("2014-07-02"));
         assertFalse(securityNode.getHistoryNode(getDate("2014-07-02")).isPresent());
         assertEquals(new BigDecimal("502.34"), price);

         assertFalse(securityNode.getHistoryNode(getDate("2014-06-30")).isPresent());
         price = Engine.getMarketPrice(investAccount.getSortedTransactionList(), securityNode, usdCurrency, getDate("2014-06-30"));
         assertEquals(new BigDecimal("501.34"), price);

         final SecurityHistoryNode future2 = new SecurityHistoryNode();
         future2.setDate(getDate("2014-07-02"));
         future2.setPrice(new BigDecimal("503.00"));
         assertTrue(e.addSecurityHistory(securityNode, future2));
         assertTrue(securityNode.getHistoryNode(getDate("2014-07-02")).isPresent());

         price = Engine.getMarketPrice(investAccount.getSortedTransactionList(), securityNode, usdCurrency, getDate("2014-07-02"));

         assertEquals(new BigDecimal("503.00"), price);

     }

     @Before
     public void setUp() {
         try {
             database = File.createTempFile("jgnash", ".bxds").getAbsolutePath();
             EngineFactory.deleteDatabase(database);

             e = EngineFactory.bootLocalEngine(database, EngineFactory.DEFAULT, PASSWORD, DataStoreType.BINARY_XSTREAM);

             // Creating currencies
             usdCurrency = DefaultCurrencies.buildCustomNode("USD");

             e.addCurrency(usdCurrency);
             e.setDefaultCurrency(usdCurrency);

             cadCurrency = DefaultCurrencies.buildCustomNode("CAD");
             e.addCurrency(cadCurrency);

             e.setExchangeRate(usdCurrency, cadCurrency, new BigDecimal("0.5"), new Date(0));

             // Creating securities
             securityNode = new SecurityNode(usdCurrency);

             securityNode.setSymbol("GOOGL");
             securityNode.setDescription("Google");
             securityNode.setScale((byte) 2);
             assertTrue(e.addSecurity(securityNode));

             // Creating accounts
             final Account incomeAccount = new Account(AccountType.INCOME, usdCurrency);
             incomeAccount.setName("Income Account");
             e.addAccount(e.getRootAccount(), incomeAccount);

             expenseAccount = new Account(AccountType.EXPENSE, usdCurrency);
             expenseAccount.setName("Expense Account");
             e.addAccount(e.getRootAccount(), expenseAccount);

             usdBankAccount = new Account(AccountType.BANK, usdCurrency);
             usdBankAccount.setName("USD Bank Account");
             e.addAccount(e.getRootAccount(), usdBankAccount);

             Account cadBankAccount = new Account(AccountType.BANK, cadCurrency);
             cadBankAccount.setName("CAD Bank Account");
             e.addAccount(e.getRootAccount(), cadBankAccount);

             Account equityAccount = new Account(AccountType.EQUITY, usdCurrency);
             equityAccount.setName("Equity Account");
             e.addAccount(e.getRootAccount(), equityAccount);

             Account liabilityAccount = new Account(AccountType.LIABILITY, usdCurrency);
             liabilityAccount.setName("Liability Account");
             e.addAccount(e.getRootAccount(), liabilityAccount);

             investAccount = new Account(AccountType.INVEST, usdCurrency);
             investAccount.setName("Invest Account");
             e.addAccount(e.getRootAccount(), investAccount);

             // Adding security to the invest account
             List<SecurityNode> securityNodeList = new ArrayList<>();
             securityNodeList.add(securityNode);
             assertTrue(e.updateAccountSecurities(investAccount, securityNodeList));
         } catch (final Exception e) {
             fail(e.getMessage());
         }
     }

     @After
     public void tearDown() {
         // Don't dump a backup
         boolean export = EngineFactory.exportXMLOnClose();
         EngineFactory.setExportXMLOnClose(false);

         EngineFactory.closeEngine(EngineFactory.DEFAULT);
         EngineFactory.deleteDatabase(database);

         // Restore the old state
         EngineFactory.setExportXMLOnClose(export);
     }
 }
