/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.account.service.custom;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountType;
import com.axelor.apps.account.db.AccountingReport;
import com.axelor.apps.account.db.AccountingReportConfigLine;
import com.axelor.apps.account.db.AccountingReportValue;
import com.axelor.apps.account.db.AnalyticAccount;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.db.repo.AccountingReportConfigLineRepository;
import com.axelor.apps.account.db.repo.AccountingReportValueRepository;
import com.axelor.apps.account.db.repo.AnalyticAccountRepository;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.exception.AccountExceptionMessage;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.DateService;
import com.axelor.auth.db.AuditableModel;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.i18n.I18n;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

public class AccountingReportValueMoveLineServiceImpl extends AccountingReportValueAbstractService
    implements AccountingReportValueMoveLineService {
  protected MoveLineRepository moveLineRepo;
  protected MoveToolService moveToolService;
  protected Set<AnalyticAccount> groupColumnAnalyticAccountSet;
  protected Set<AnalyticAccount> columnAnalyticAccountSet;
  protected Set<AnalyticAccount> lineAnalyticAccountSet;

  @Inject
  public AccountingReportValueMoveLineServiceImpl(
      AccountRepository accountRepository,
      AccountingReportValueRepository accountingReportValueRepo,
      AnalyticAccountRepository analyticAccountRepo,
      MoveLineRepository moveLineRepo,
      DateService dateService,
      MoveToolService moveToolService) {
    super(accountRepository, accountingReportValueRepo, analyticAccountRepo, dateService);
    this.moveLineRepo = moveLineRepo;
    this.moveToolService = moveToolService;
  }

  @Override
  public void createValueFromMoveLines(
      AccountingReport accountingReport,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      Map<String, Map<String, AccountingReportValue>> valuesMapByColumn,
      Map<String, Map<String, AccountingReportValue>> valuesMapByLine,
      Account groupAccount,
      Set<Company> companySet,
      AnalyticAccount configAnalyticAccount,
      String parentTitle,
      LocalDate startDate,
      LocalDate endDate,
      int analyticCounter)
      throws AxelorException {
    this.checkResultSelects(accountingReport, groupColumn, column, line);

    if (accountingReport.getDisplayDetails()
        && line.getDetailBySelect()
            == AccountingReportConfigLineRepository.DETAIL_BY_ACCOUNT_TYPE) {
      int counter = 1;

      Set<AccountType> accountTypeSet =
          this.sortSet(this.getAccountTypeSet(line), Comparator.comparing(AccountType::getName));

      for (AccountType accountType : accountTypeSet) {
        String lineCode = String.format("%s_%d", line.getCode(), counter++);

        if (!valuesMapByLine.containsKey(lineCode)) {
          valuesMapByLine.put(lineCode, new HashMap<>());
        }

        accountingReport = this.fetchAccountingReport(accountingReport);

        accountType = JPA.find(AccountType.class, accountType.getId());
        line = JPA.find(AccountingReportConfigLine.class, line.getId());
        column = JPA.find(AccountingReportConfigLine.class, column.getId());
        groupColumn =
            groupColumn != null
                ? JPA.find(AccountingReportConfigLine.class, groupColumn.getId())
                : null;
        configAnalyticAccount =
            configAnalyticAccount != null
                ? JPA.find(AnalyticAccount.class, configAnalyticAccount.getId())
                : null;

        Set<Long> accountIdSet =
            this.getAccountIdSet(line, new HashSet<>(Collections.singleton(accountType)));

        this.mergeSetsAndCreateValueFromMoveLines(
            accountingReport,
            groupColumn,
            column,
            line,
            valuesMapByColumn,
            valuesMapByLine,
            groupAccount,
            companySet,
            configAnalyticAccount,
            accountIdSet,
            null,
            parentTitle,
            accountType.getName(),
            lineCode,
            startDate,
            endDate,
            analyticCounter);

        JPA.clear();
        AccountingReportValueServiceImpl.incrementLineOffset();
      }
    } else {
      Set<Long> accountIdSet = this.getAccountIdSet(line);

      if (accountingReport.getDisplayDetails()
          && line.getDetailBySelect() == AccountingReportConfigLineRepository.DETAIL_BY_ACCOUNT) {
        int counter = 1;

        for (Long accountId : accountIdSet) {
          String lineCode = String.format("%s_%d", line.getCode(), counter++);

          if (!valuesMapByLine.containsKey(lineCode)) {
            valuesMapByLine.put(lineCode, new HashMap<>());
          }

          accountingReport = this.fetchAccountingReport(accountingReport);

          Account account = JPA.find(Account.class, accountId);
          line = JPA.find(AccountingReportConfigLine.class, line.getId());
          column = JPA.find(AccountingReportConfigLine.class, column.getId());
          groupColumn =
              groupColumn != null
                  ? JPA.find(AccountingReportConfigLine.class, groupColumn.getId())
                  : null;
          configAnalyticAccount =
              configAnalyticAccount != null
                  ? JPA.find(AnalyticAccount.class, configAnalyticAccount.getId())
                  : null;

          this.mergeSetsAndCreateValueFromMoveLines(
              accountingReport,
              groupColumn,
              column,
              line,
              valuesMapByColumn,
              valuesMapByLine,
              groupAccount,
              companySet,
              configAnalyticAccount,
              new HashSet<>(Collections.singleton(accountId)),
              null,
              parentTitle,
              account.getLabel(),
              lineCode,
              startDate,
              endDate,
              analyticCounter);

          JPA.clear();
          AccountingReportValueServiceImpl.incrementLineOffset();
        }
      } else if (accountingReport.getDisplayDetails()
          && line.getDetailBySelect()
              == AccountingReportConfigLineRepository.DETAIL_BY_ANALYTIC_ACCOUNT) {
        int counter = 1;
        Set<AnalyticAccount> sortedAnalyticAccountSet =
            this.sortSet(
                line.getAnalyticAccountSet(), Comparator.comparing(AnalyticAccount::getFullName));

        for (AnalyticAccount analyticAccount : sortedAnalyticAccountSet) {
          String lineCode = String.format("%s_%d", line.getCode(), counter++);

          if (!valuesMapByLine.containsKey(lineCode)) {
            valuesMapByLine.put(lineCode, new HashMap<>());
          }

          accountingReport = this.fetchAccountingReport(accountingReport);

          analyticAccount = JPA.find(AnalyticAccount.class, analyticAccount.getId());
          line = JPA.find(AccountingReportConfigLine.class, line.getId());
          column = JPA.find(AccountingReportConfigLine.class, column.getId());
          groupColumn =
              groupColumn != null
                  ? JPA.find(AccountingReportConfigLine.class, groupColumn.getId())
                  : null;
          configAnalyticAccount =
              configAnalyticAccount != null
                  ? JPA.find(AnalyticAccount.class, configAnalyticAccount.getId())
                  : null;

          this.mergeSetsAndCreateValueFromMoveLines(
              accountingReport,
              groupColumn,
              column,
              line,
              valuesMapByColumn,
              valuesMapByLine,
              groupAccount,
              companySet,
              configAnalyticAccount,
              accountIdSet,
              analyticAccount,
              parentTitle,
              analyticAccount.getFullName(),
              lineCode,
              startDate,
              endDate,
              analyticCounter);

          JPA.clear();
          AccountingReportValueServiceImpl.incrementLineOffset();
        }
      } else {
        this.mergeSetsAndCreateValueFromMoveLines(
            accountingReport,
            groupColumn,
            column,
            line,
            valuesMapByColumn,
            valuesMapByLine,
            groupAccount,
            companySet,
            configAnalyticAccount,
            accountIdSet,
            null,
            parentTitle,
            line.getLabel(),
            line.getCode(),
            startDate,
            endDate,
            analyticCounter);
      }
    }
  }

  protected Set<Account> getAccountSet(
      AccountingReportConfigLine configLine, Set<AccountType> accountTypeSet) {
    Set<Account> accountSet = configLine.getAccountSet();
    accountSet = this.mergeWithAccountTypes(accountSet, accountTypeSet);
    accountSet = this.mergeWithAccountCode(accountSet, configLine.getAccountCode());
    return this.sortSet(accountSet, Comparator.comparing(Account::getLabel));
  }

  protected Set<Long> getAccountIdSet(
      AccountingReportConfigLine configLine, Set<AccountType> accountTypeSet) {
    return this.getAccountSet(configLine, accountTypeSet).stream()
        .map(Account::getId)
        .collect(Collectors.toSet());
  }

  protected Set<Long> getAccountIdSet(AccountingReportConfigLine configLine) {
    return this.getAccountIdSet(configLine, configLine.getAccountTypeSet());
  }

  protected Set<AccountType> getAccountTypeSet(AccountingReportConfigLine configLine) {
    return this.getAccountSet(configLine, configLine.getAccountTypeSet()).stream()
        .map(Account::getAccountType)
        .collect(Collectors.toSet());
  }

  protected <T extends AuditableModel> Set<T> sortSet(Set<T> set, Comparator<T> comparator) {
    Set<T> sortedSet = new TreeSet<>(comparator);
    sortedSet.addAll(set);

    return sortedSet;
  }

  protected void mergeSetsAndCreateValueFromMoveLines(
      AccountingReport accountingReport,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      Map<String, Map<String, AccountingReportValue>> valuesMapByColumn,
      Map<String, Map<String, AccountingReportValue>> valuesMapByLine,
      Account groupAccount,
      Set<Company> companySet,
      AnalyticAccount configAnalyticAccount,
      Set<Long> accountIdSet,
      AnalyticAccount detailByAnalyticAccount,
      String parentTitle,
      String lineTitle,
      String lineCode,
      LocalDate startDate,
      LocalDate endDate,
      int analyticCounter)
      throws AxelorException {
    Set<AnalyticAccount> lineAnalyticAccountSet = line.getAnalyticAccountSet();

    if (detailByAnalyticAccount != null) {
      lineAnalyticAccountSet = this.getParentAnalyticAccountSet(detailByAnalyticAccount);
    }

    Set<AnalyticAccount> analyticAccountSet =
        this.mergeSets(column.getAnalyticAccountSet(), lineAnalyticAccountSet);
    analyticAccountSet =
        this.mergeSets(analyticAccountSet, accountingReport.getAnalyticAccountSet());

    if (groupAccount != null) {
      accountIdSet = new HashSet<>(Collections.singletonList(groupAccount.getId()));
    } else {
      accountIdSet = this.mergeSets(accountIdSet, this.getAccountIdSet(column));

      if (CollectionUtils.isNotEmpty(accountingReport.getAccountSet())) {
        accountIdSet =
            this.mergeSets(
                accountIdSet,
                accountingReport.getAccountSet().stream()
                    .map(Account::getId)
                    .collect(Collectors.toSet()));
      }

      if (groupColumn != null) {
        accountIdSet = this.mergeSets(accountIdSet, this.getAccountIdSet(groupColumn));
        analyticAccountSet =
            this.mergeSets(groupColumn.getAnalyticAccountSet(), analyticAccountSet);
      }
    }

    this.createValueFromMoveLine(
        accountingReport,
        groupColumn,
        column,
        line,
        valuesMapByColumn,
        valuesMapByLine,
        accountIdSet,
        analyticAccountSet,
        companySet,
        configAnalyticAccount,
        startDate,
        endDate,
        parentTitle,
        lineTitle,
        lineCode,
        analyticCounter);
  }

  protected Set<AnalyticAccount> getParentAnalyticAccountSet(AnalyticAccount analyticAccount) {
    List<AnalyticAccount> parentAnalyticAccountList =
        analyticAccountRepo.findByParent(analyticAccount).fetch();

    if (parentAnalyticAccountList.isEmpty()) {
      return new HashSet<>(Collections.singletonList(analyticAccount));
    } else {
      return parentAnalyticAccountList.stream()
          .map(this::getParentAnalyticAccountSet)
          .flatMap(Collection::stream)
          .collect(Collectors.toSet());
    }
  }

  protected void checkResultSelects(
      AccountingReport accountingReport,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line)
      throws AxelorException {
    List<Integer> basicResultSelectList =
        Arrays.asList(
            AccountingReportConfigLineRepository.RESULT_DEBIT_MINUS_CREDIT,
            AccountingReportConfigLineRepository.RESULT_DEBIT,
            AccountingReportConfigLineRepository.RESULT_CREDIT);

    boolean isBasicResultSelect =
        basicResultSelectList.contains(column.getResultSelect())
            || basicResultSelectList.contains(line.getResultSelect());
    boolean isOnlyBasicResultSelect =
        basicResultSelectList.contains(column.getResultSelect())
            && basicResultSelectList.contains(line.getResultSelect());
    boolean isGroupResultSelect =
        column.getResultSelect() == AccountingReportConfigLineRepository.RESULT_SAME_AS_GROUP
            || line.getResultSelect() == AccountingReportConfigLineRepository.RESULT_SAME_AS_GROUP;
    boolean isSameResultResult =
        Objects.equals(column.getResultSelect(), line.getResultSelect())
            && Objects.equals(column.getNegateValue(), line.getNegateValue());
    boolean isSameSignum = column.getNegateValue() == line.getNegateValue();

    if (isOnlyBasicResultSelect && (!isSameResultResult || !isSameSignum)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(AccountExceptionMessage.REPORT_TYPE_DIFFERENT_RESULT_SELECT));
    } else if (!isBasicResultSelect && !isGroupResultSelect) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(AccountExceptionMessage.REPORT_TYPE_NO_RESULT_SELECT));
    } else if (isGroupResultSelect && groupColumn == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(AccountExceptionMessage.REPORT_TYPE_SAME_AS_GROUP_NO_GROUP));
    }
  }

  protected <T> Set<T> mergeSets(Set<T> set1, Set<T> set2) {
    if (CollectionUtils.isEmpty(set1)) {
      return set2;
    } else if (CollectionUtils.isEmpty(set2)) {
      return set1;
    } else {
      Set<T> finalSet = new HashSet<>(set1);
      finalSet.retainAll(set2);
      return finalSet;
    }
  }

  protected Set<Account> mergeWithAccountTypes(
      Set<Account> accountSet, Set<AccountType> accountTypeSet) {
    if (CollectionUtils.isEmpty(accountTypeSet)) {
      return accountSet;
    }

    Set<Account> tempSet =
        this.getSetFromStream(
            accountTypeSet.stream(),
            accountType -> accountRepo.findByAccountType(accountType).fetch());

    return this.mergeSets(accountSet, tempSet);
  }

  protected Set<Account> mergeWithAccountCode(Set<Account> accountSet, String accountCode) {
    if (StringUtils.isEmpty(accountCode)) {
      return accountSet;
    }

    Set<Account> tempSet =
        this.getSetFromStream(
            Arrays.stream(accountCode.split(",")),
            accountCodeToken ->
                accountRepo.all().filter("self.code LIKE ?", accountCodeToken).fetch());

    return this.mergeSets(accountSet, tempSet);
  }

  protected <T> Set<Account> getSetFromStream(
      Stream<T> stream, Function<T, List<Account>> function) {
    return stream.map(function).flatMap(Collection::stream).collect(Collectors.toSet());
  }

  protected Set<AccountType> mergeWithAccounts(
      Set<AccountType> accountTypeSet, Set<Account> accountSet) {
    if (CollectionUtils.isEmpty(accountSet)) {
      return accountTypeSet;
    }

    Set<AccountType> tempSet =
        accountSet.stream().map(Account::getAccountType).collect(Collectors.toSet());

    return this.mergeSets(accountTypeSet, tempSet);
  }

  protected void createValueFromMoveLine(
      AccountingReport accountingReport,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      Map<String, Map<String, AccountingReportValue>> valuesMapByColumn,
      Map<String, Map<String, AccountingReportValue>> valuesMapByLine,
      Set<Long> accountIdSet,
      Set<AnalyticAccount> analyticAccountSet,
      Set<Company> companySet,
      AnalyticAccount configAnalyticAccount,
      LocalDate startDate,
      LocalDate endDate,
      String parentTitle,
      String lineTitle,
      String lineCode,
      int analyticCounter)
      throws AxelorException {
    Set<AnalyticAccount> resultAnalyticAccountSet =
        this.mergeSets(
            analyticAccountSet,
            configAnalyticAccount == null
                ? null
                : new HashSet<>(Collections.singletonList(configAnalyticAccount)));

    List<MoveLine> moveLineList =
        this.getMoveLineQuery(
                accountingReport,
                groupColumn,
                column,
                line,
                accountIdSet,
                resultAnalyticAccountSet,
                companySet,
                startDate,
                endDate)
            .fetch();

    if (line.getHideDetailedLinesWithoutMoves() && moveLineList.isEmpty()) {
      return;
    }

    BigDecimal result =
        this.getResultFromMoveLine(
            accountingReport,
            groupColumn,
            column,
            line,
            moveLineList,
            resultAnalyticAccountSet,
            startDate,
            endDate,
            this.getResultSelect(column, line, groupColumn));

    this.createReportValue(
        accountingReport,
        column,
        line,
        groupColumn,
        startDate,
        endDate,
        parentTitle,
        lineTitle,
        result,
        valuesMapByColumn,
        valuesMapByLine,
        companySet,
        configAnalyticAccount,
        lineCode,
        analyticCounter);
  }

  protected int getResultSelect(
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      AccountingReportConfigLine groupColumn) {
    if (column.getResultSelect() == AccountingReportConfigLineRepository.RESULT_SAME_AS_GROUP
        || line.getResultSelect() == AccountingReportConfigLineRepository.RESULT_SAME_AS_GROUP) {
      return groupColumn.getResultSelect();
    } else if (column.getResultSelect()
        == AccountingReportConfigLineRepository.RESULT_SAME_AS_LINE) {
      return line.getResultSelect();
    } else {
      return column.getResultSelect();
    }
  }

  protected Query<MoveLine> getMoveLineQuery(
      AccountingReport accountingReport,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      Set<Long> accountIdSet,
      Set<AnalyticAccount> analyticAccountSet,
      Set<Company> companySet,
      LocalDate startDate,
      LocalDate endDate) {
    Pair<LocalDate, LocalDate> dates =
        this.getDates(accountingReport, groupColumn, column, line, startDate, endDate);

    return this.buildMoveLineQuery(
        accountingReport,
        accountIdSet,
        analyticAccountSet,
        companySet,
        groupColumn,
        column,
        line,
        dates.getLeft(),
        dates.getRight());
  }

  protected Pair<LocalDate, LocalDate> getDates(
      AccountingReport accountingReport,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      LocalDate startDate,
      LocalDate endDate) {
    Pair<LocalDate, LocalDate> dates;

    if (column.getComputePreviousYear()) {
      dates = Pair.of(startDate.minusYears(1), endDate.minusYears(1));
    } else if (this.isComputeOnOtherPeriod(groupColumn, column)) {
      dates = Pair.of(accountingReport.getOtherDateFrom(), accountingReport.getOtherDateTo());
    } else {
      dates = Pair.of(startDate, endDate);
    }

    if (line.getBalanceBeforePeriod()
        || column.getBalanceBeforePeriod()
        || (groupColumn != null && groupColumn.getBalanceBeforePeriod())) {
      dates = Pair.of(LocalDate.of(1900, 1, 1), dates.getLeft().minusDays(1));
    }

    return dates;
  }

  protected boolean isComputeOnOtherPeriod(
      AccountingReportConfigLine groupColumn, AccountingReportConfigLine column) {
    return column.getComputeOtherPeriod()
        || (groupColumn != null && groupColumn.getComputeOtherPeriod());
  }

  protected Query<MoveLine> buildMoveLineQuery(
      AccountingReport accountingReport,
      Set<Long> accountIdSet,
      Set<AnalyticAccount> analyticAccountSet,
      Set<Company> companySet,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      LocalDate startDate,
      LocalDate endDate) {
    return moveLineRepo
        .all()
        .filter(
            this.getMoveLineQuery(
                accountingReport,
                accountIdSet,
                analyticAccountSet,
                companySet,
                groupColumn,
                column,
                line))
        .bind("dateFrom", startDate)
        .bind("dateTo", endDate)
        .bind("journal", accountingReport.getJournal())
        .bind("paymentMode", accountingReport.getPaymentMode())
        .bind("currency", accountingReport.getCurrency())
        .bind("companySet", companySet)
        .bind(
            "statusList",
            moveToolService.getMoveStatusSelect(accountingReport.getMoveStatusSelect(), companySet))
        .bind("accountIdSet", accountIdSet)
        .bind("analyticAccountSet", analyticAccountSet);
  }

  protected String getMoveLineQuery(
      AccountingReport accountingReport,
      Set<Long> accountIdSet,
      Set<AnalyticAccount> analyticAccountSet,
      Set<Company> companySet,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line) {
    List<String> queryList =
        new ArrayList<>(Collections.singletonList("self.move.statusSelect IN :statusList"));

    queryList.add(
        String.format(
            "(self.account.id IN %s)",
            CollectionUtils.isEmpty(accountIdSet) ? "(0)" : ":accountIdSet"));

    this.addDateQueries(queryList, accountingReport);

    if (accountingReport.getJournal() != null) {
      queryList.add("(self.move.journal IS NULL OR self.move.journal = :journal)");
    }

    if (accountingReport.getPaymentMode() != null) {
      queryList.add("(self.move.paymentMode IS NULL OR self.move.paymentMode = :paymentMode)");
    }

    if (accountingReport.getCurrency() != null) {
      queryList.add("(self.move.currency IS NULL OR self.move.currency = :currency)");
    }

    if (CollectionUtils.isNotEmpty(companySet)) {
      queryList.add("(self.move.company IS NULL OR self.move.company IN :companySet)");
    }

    if (!this.areAllAnalyticAccountSetsEmpty(accountingReport, groupColumn, column, line)) {
      queryList.add(
          "EXISTS(SELECT 1 FROM AnalyticMoveLine aml WHERE aml.analyticAccount IN :analyticAccountSet AND aml.moveLine = self)");
    }

    if (groupColumn != null && !Strings.isNullOrEmpty(groupColumn.getAnalyticAccountCode())) {
      queryList.add(
          "EXISTS(SELECT 1 FROM AnalyticMoveLine aml WHERE aml.analyticAccount.code LIKE :groupColumnAnalyticAccountFilter AND aml.moveLine = self)");
    }

    if (!Strings.isNullOrEmpty(column.getAnalyticAccountCode())) {
      queryList.add(
          "EXISTS(SELECT 1 FROM AnalyticMoveLine aml WHERE aml.analyticAccount.code LIKE :columnAnalyticAccountFilter AND aml.moveLine = self)");
    }

    if (!Strings.isNullOrEmpty(line.getAnalyticAccountCode())) {
      queryList.add(
          "EXISTS(SELECT 1 FROM AnalyticMoveLine aml WHERE aml.analyticAccount.code LIKE :lineAnalyticAccountFilter AND aml.moveLine = self)");
    }

    return String.join(" AND ", queryList);
  }

  protected void addDateQueries(List<String> queryList, AccountingReport accountingReport) {
    if (accountingReport.getDateFrom() != null) {
      queryList.add("(self.date IS NULL OR self.date >= :dateFrom)");
    }

    if (accountingReport.getDateTo() != null) {
      queryList.add("(self.date IS NULL OR self.date <= :dateTo)");
    }
  }

  protected BigDecimal getResultFromMoveLine(
      AccountingReport accountingReport,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      List<MoveLine> moveLineList,
      Set<AnalyticAccount> analyticAccountSet,
      LocalDate startDate,
      LocalDate endDate,
      int resultSelect) {
    String groupColumnAnalyticAccountCode =
        groupColumn != null ? groupColumn.getAnalyticAccountCode() : null;
    String columnAnalyticAccountCode = column.getAnalyticAccountCode();
    String lineAnalyticAccountCode = line.getAnalyticAccountCode();
    groupColumnAnalyticAccountSet =
        groupColumn != null && StringUtils.notEmpty(groupColumnAnalyticAccountCode)
            ? this.fetchAnalyticAccountsFromCode(groupColumnAnalyticAccountCode)
            : new HashSet<>();
    columnAnalyticAccountSet =
        StringUtils.notEmpty(columnAnalyticAccountCode)
            ? this.fetchAnalyticAccountsFromCode(columnAnalyticAccountCode)
            : new HashSet<>();
    lineAnalyticAccountSet =
        StringUtils.notEmpty(lineAnalyticAccountCode)
            ? this.fetchAnalyticAccountsFromCode(lineAnalyticAccountCode)
            : new HashSet<>();
    return moveLineList.stream()
        .map(
            it ->
                this.getMoveLineAmount(
                    it,
                    accountingReport,
                    groupColumn,
                    column,
                    line,
                    analyticAccountSet,
                    startDate,
                    endDate,
                    resultSelect))
        .reduce(BigDecimal::add)
        .orElse(BigDecimal.ZERO);
  }

  protected BigDecimal getMoveLineAmount(
      MoveLine moveLine,
      AccountingReport accountingReport,
      AccountingReportConfigLine groupColumn,
      AccountingReportConfigLine column,
      AccountingReportConfigLine line,
      Set<AnalyticAccount> analyticAccountSet,
      LocalDate startDate,
      LocalDate endDate,
      int resultSelect) {
    String groupColumnAnalyticAccountCode =
        groupColumn == null ? null : groupColumn.getAnalyticAccountCode();

    BigDecimal value = BigDecimal.ZERO;

    if (CollectionUtils.isNotEmpty(analyticAccountSet)
        || StringUtils.notEmpty(groupColumnAnalyticAccountCode)
        || StringUtils.notEmpty(column.getAnalyticAccountCode())
        || StringUtils.notEmpty(line.getAnalyticAccountCode())) {
      value =
          this.getAnalyticAmount(
              moveLine, analyticAccountSet, resultSelect, moveLine.getDebit().signum() > 0);
    } else {
      switch (resultSelect) {
        case AccountingReportConfigLineRepository.RESULT_DEBIT_MINUS_CREDIT:
          value = moveLine.getDebit().subtract(moveLine.getCredit());
          break;
        case AccountingReportConfigLineRepository.RESULT_DEBIT:
          value = moveLine.getDebit();
          break;
        case AccountingReportConfigLineRepository.RESULT_CREDIT:
          value = moveLine.getCredit();
          break;
      }
    }

    if ((groupColumn != null && groupColumn.getNegateValue())
        || column.getNegateValue()
        || line.getNegateValue()) {
      value = value.negate();
    }

    return value;
  }

  protected BigDecimal getAnalyticAmount(
      MoveLine moveLine,
      Set<AnalyticAccount> analyticAccountSet,
      int resultSelect,
      boolean isDebit) {
    if (CollectionUtils.isEmpty(moveLine.getAnalyticMoveLineList())) {
      return BigDecimal.ZERO;
    }

    BigDecimal value =
        moveLine.getAnalyticMoveLineList().stream()
            .filter(it -> this.containsAnalyticAccount(it.getAnalyticAccount(), analyticAccountSet))
            .map(AnalyticMoveLine::getAmount)
            .reduce(BigDecimal::add)
            .orElse(BigDecimal.ZERO);

    switch (resultSelect) {
      case AccountingReportConfigLineRepository.RESULT_DEBIT_MINUS_CREDIT:
        return isDebit ? value : value.negate();
      case AccountingReportConfigLineRepository.RESULT_DEBIT:
        return isDebit ? value : BigDecimal.ZERO;
      case AccountingReportConfigLineRepository.RESULT_CREDIT:
        return isDebit ? BigDecimal.ZERO : value;
    }

    return BigDecimal.ZERO;
  }

  protected boolean containsAnalyticAccount(
      AnalyticAccount analyticAccount, Set<AnalyticAccount> analyticAccountSet) {
    return (CollectionUtils.isNotEmpty(analyticAccountSet)
            && analyticAccountSet.contains(analyticAccount))
        || groupColumnAnalyticAccountSet.contains(analyticAccount)
        || columnAnalyticAccountSet.contains(analyticAccount)
        || lineAnalyticAccountSet.contains(analyticAccount);
  }
}
