/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.production.service.configurator;

import com.axelor.apps.production.db.ConfiguratorProdProcessLine;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.WorkCenter;
import com.axelor.apps.production.db.WorkCenterGroup;
import com.axelor.apps.production.db.repo.ConfiguratorProdProcessLineRepository;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.apps.production.service.WorkCenterService;
import com.axelor.apps.sale.service.configurator.ConfiguratorService;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.JsonContext;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;

public class ConfiguratorProdProcessLineServiceImpl implements ConfiguratorProdProcessLineService {

  protected ConfiguratorService configuratorService;
  protected WorkCenterService workCenterService;

  @Inject
  public ConfiguratorProdProcessLineServiceImpl(
      ConfiguratorService configuratorService, WorkCenterService workCenterService) {
    this.configuratorService = configuratorService;
    this.workCenterService = workCenterService;
  }

  @Override
  public ProdProcessLine generateProdProcessLine(
      ConfiguratorProdProcessLine confProdProcessLine, JsonContext attributes)
      throws AxelorException {
    if (confProdProcessLine == null || !checkConditions(confProdProcessLine, attributes)) {
      return null;
    }

    String name;
    Integer priority;
    StockLocation stockLocation;
    ProdProcessLine prodProcessLine = new ProdProcessLine();
    BigDecimal minCapacityPerCycle;
    BigDecimal maxCapacityPerCycle;
    long durationPerCycle;

    if (confProdProcessLine.getDefNameAsFormula()) {
      Object computedName =
          configuratorService.computeFormula(confProdProcessLine.getNameFormula(), attributes);
      if (computedName == null) {
        throw new AxelorException(
            confProdProcessLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(
                String.format(
                    IExceptionMessage.CONFIGURATOR_PROD_PROCESS_LINE_INCONSISTENT_NAME_FORMULA,
                    confProdProcessLine.getId())));
      } else {
        name = String.valueOf(computedName);
      }
    } else {
      name = confProdProcessLine.getName();
      if (name == null) {
        throw new AxelorException(
            confProdProcessLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(
                String.format(
                    IExceptionMessage.CONFIGURATOR_PROD_PROCESS_LINE_INCONSISTENT_NULL_NAME,
                    confProdProcessLine.getId())));
      }
    }
    if (confProdProcessLine.getDefPriorityAsFormula()) {
      Object computedPriority =
          configuratorService.computeFormula(confProdProcessLine.getPriorityFormula(), attributes);
      if (computedPriority != null) {
        priority = new Integer(String.valueOf(computedPriority));
      } else {
        priority = 0;
      }

    } else {
      priority = confProdProcessLine.getPriority();
    }
    if (confProdProcessLine.getDefStockLocationAsFormula()) {
      stockLocation =
          (StockLocation)
              configuratorService.computeFormula(
                  confProdProcessLine.getStockLocationFormula(), attributes);
    } else {
      stockLocation = confProdProcessLine.getStockLocation();
    }
    if (confProdProcessLine.getDefMinCapacityFormula()) {
      minCapacityPerCycle =
          new BigDecimal(
              configuratorService
                  .computeFormula(confProdProcessLine.getMinCapacityPerCycleFormula(), attributes)
                  .toString());
    } else {
      minCapacityPerCycle = confProdProcessLine.getMinCapacityPerCycle();
    }
    if (confProdProcessLine.getDefMaxCapacityFormula()) {
      maxCapacityPerCycle =
          new BigDecimal(
              configuratorService
                  .computeFormula(confProdProcessLine.getMaxCapacityPerCycleFormula(), attributes)
                  .toString());
    } else {
      maxCapacityPerCycle = confProdProcessLine.getMaxCapacityPerCycle();
    }
    if (confProdProcessLine.getDefDurationFormula()) {
      durationPerCycle =
          Long.decode(
              configuratorService
                  .computeFormula(confProdProcessLine.getDurationPerCycleFormula(), attributes)
                  .toString());
    } else {
      durationPerCycle = confProdProcessLine.getDurationPerCycle();
    }

    prodProcessLine.setName(name);
    prodProcessLine.setPriority(priority);
    prodProcessLine.setWorkCenter(confProdProcessLine.getWorkCenter());
    prodProcessLine.setWorkCenterTypeSelect(confProdProcessLine.getWorkCenterTypeSelect());
    prodProcessLine.setWorkCenterGroup(confProdProcessLine.getWorkCenterGroup());
    prodProcessLine.setOutsourcing(confProdProcessLine.getOutsourcing());
    prodProcessLine.setStockLocation(stockLocation);
    prodProcessLine.setDescription(confProdProcessLine.getDescription());
    prodProcessLine.setMinCapacityPerCycle(minCapacityPerCycle);
    prodProcessLine.setMaxCapacityPerCycle(maxCapacityPerCycle);
    prodProcessLine.setDurationPerCycle(durationPerCycle);

    return prodProcessLine;
  }

  protected boolean checkConditions(
      ConfiguratorProdProcessLine confProdProcessLine, JsonContext jsonAttributes)
      throws AxelorException {
    String condition = confProdProcessLine.getUseCondition();
    // no condition = we always generate the prod process line
    if (condition == null || condition.trim().isEmpty()) {
      return true;
    }

    Object computedConditions = configuratorService.computeFormula(condition, jsonAttributes);
    if (computedConditions == null) {
      throw new AxelorException(
          confProdProcessLine,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(
              String.format(
                  IExceptionMessage.CONFIGURATOR_PROD_PROCESS_LINE_INCONSISTENT_CONDITION,
                  confProdProcessLine.getId())));
    }

    return (boolean) computedConditions;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void setWorkCenterGroup(
      ConfiguratorProdProcessLine confProdProcessLine, WorkCenterGroup workCenterGroup)
      throws AxelorException {
    confProdProcessLine = copyWorkCenterGroup(confProdProcessLine, workCenterGroup);
    WorkCenter workCenter =
        workCenterService.getMainWorkCenterFromGroup(confProdProcessLine.getWorkCenterGroup());
    confProdProcessLine.setWorkCenter(workCenter);
    confProdProcessLine.setDurationPerCycle(
        workCenterService.getDurationFromWorkCenter(workCenter));
    confProdProcessLine.setMinCapacityPerCycle(
        workCenterService.getMinCapacityPerCycleFromWorkCenter(workCenter));
    confProdProcessLine.setMaxCapacityPerCycle(
        workCenterService.getMaxCapacityPerCycleFromWorkCenter(workCenter));
  }

  /**
   * Create a work center group from a template. Since a template is also a work center group, we
   * copy and set template field to false.
   */
  protected ConfiguratorProdProcessLine copyWorkCenterGroup(
      ConfiguratorProdProcessLine confProdProcessLine, WorkCenterGroup workCenterGroup) {
    WorkCenterGroup workCenterGroupCopy = JPA.copy(workCenterGroup, false);
    workCenterGroupCopy.setWorkCenterGroupModel(workCenterGroup);
    workCenterGroupCopy.setTemplate(false);
    workCenterGroup.getWorkCenterSet().forEach((workCenterGroupCopy::addWorkCenterSetItem));

    confProdProcessLine.setWorkCenterGroup(workCenterGroupCopy);
    return Beans.get(ConfiguratorProdProcessLineRepository.class).save(confProdProcessLine);
  }
}
