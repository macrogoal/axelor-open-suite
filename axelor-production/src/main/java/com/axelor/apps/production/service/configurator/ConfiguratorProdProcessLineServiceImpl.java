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
package com.axelor.apps.production.service.configurator;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.production.db.ConfiguratorProdProcessLine;
import com.axelor.apps.production.db.ConfiguratorProdProduct;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.ProdProduct;
import com.axelor.apps.production.db.WorkCenter;
import com.axelor.apps.production.exceptions.ProductionExceptionMessage;
import com.axelor.apps.production.service.WorkCenterService;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.sale.service.configurator.ConfiguratorService;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.i18n.I18n;
import com.axelor.rpc.JsonContext;
import com.axelor.studio.db.AppProduction;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;

public class ConfiguratorProdProcessLineServiceImpl implements ConfiguratorProdProcessLineService {

  protected ConfiguratorService configuratorService;
  protected WorkCenterService workCenterService;
  protected AppProductionService appProdService;
  protected ConfiguratorProdProductService confProdProductService;

  @Inject
  public ConfiguratorProdProcessLineServiceImpl(
      ConfiguratorService configuratorService,
      WorkCenterService workCenterService,
      AppProductionService appProdService,
      ConfiguratorProdProductService confProdProductService) {
    this.configuratorService = configuratorService;
    this.workCenterService = workCenterService;
    this.appProdService = appProdService;
    this.confProdProductService = confProdProductService;
  }

  @Override
  public ProdProcessLine generateProdProcessLine(
      ConfiguratorProdProcessLine confProdProcessLine,
      boolean isConsProOnOperation,
      JsonContext attributes)
      throws AxelorException {
    if (confProdProcessLine == null || !checkConditions(confProdProcessLine, attributes)) {
      return null;
    }

    String name;
    Integer priority;
    StockLocation stockLocation;
    String description;
    WorkCenter workCenter = null;
    ProdProcessLine prodProcessLine = new ProdProcessLine();
    BigDecimal minCapacityPerCycle;
    BigDecimal maxCapacityPerCycle;
    long durationPerCycle;
    long humanDuration;

    if (confProdProcessLine.getDefNameAsFormula()) {
      Object computedName =
          configuratorService.computeFormula(confProdProcessLine.getNameFormula(), attributes);
      if (computedName == null) {
        throw new AxelorException(
            confProdProcessLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            String.format(
                I18n.get(
                    ProductionExceptionMessage
                        .CONFIGURATOR_PROD_PROCESS_LINE_INCONSISTENT_NAME_FORMULA),
                confProdProcessLine.getId()));
      } else {
        name = String.valueOf(computedName);
      }
    } else {
      name = confProdProcessLine.getName();
      if (name == null) {
        throw new AxelorException(
            confProdProcessLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            String.format(
                I18n.get(
                    ProductionExceptionMessage
                        .CONFIGURATOR_PROD_PROCESS_LINE_INCONSISTENT_NULL_NAME),
                confProdProcessLine.getId()));
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
    if (confProdProcessLine.getDefDescriptionAsFormula()) {
      description =
          String.valueOf(
              configuratorService.computeFormula(
                  confProdProcessLine.getDescriptionFormula(), attributes));
    } else {
      description = confProdProcessLine.getDescription();
    }

    AppProduction appProd = appProdService.getAppProduction();

    if (appProd != null && appProd.getManageWorkCenterGroup()) {

      if (confProdProcessLine.getWorkCenterGroup() == null) {
        throw new AxelorException(
            confProdProcessLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            String.format(
                I18n.get(
                    ProductionExceptionMessage
                        .CONFIGURATOR_PROD_PROCESS_LINE_INCONSISTENT_NULL_WORK_CENTER_GROUP),
                confProdProcessLine.getId()));
      }
    }
    if (confProdProcessLine.getDefWorkCenterAsFormula()) {
      Object computedWorkCenter =
          configuratorService.computeFormula(
              confProdProcessLine.getWorkCenterFormula(), attributes);
      if (computedWorkCenter == null) {
        throw new AxelorException(
            confProdProcessLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            String.format(
                I18n.get(
                    ProductionExceptionMessage
                        .CONFIGURATOR_PROD_PROCESS_LINE_INCONSISTENT_WORK_CENTER_FORMULA),
                confProdProcessLine.getId()));
      } else {
        workCenter = (WorkCenter) computedWorkCenter;
      }
    } else {
      workCenter = confProdProcessLine.getWorkCenter();

      if (workCenter == null && appProd != null && appProd.getManageWorkCenterGroup()) {
        workCenter =
            workCenterService.getMainWorkCenterFromGroup(confProdProcessLine.getWorkCenterGroup());
      }
      if (workCenter == null) {
        throw new AxelorException(
            confProdProcessLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            String.format(
                I18n.get(
                    ProductionExceptionMessage
                        .CONFIGURATOR_PROD_PROCESS_LINE_INCONSISTENT_NULL_WORK_CENTER),
                confProdProcessLine.getId()));
      }
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
    if (confProdProcessLine.getDefHrDurationFormula()) {
      humanDuration =
          Long.decode(
              configuratorService
                  .computeFormula(confProdProcessLine.getHumanDurationFormula(), attributes)
                  .toString());
    } else {
      humanDuration = confProdProcessLine.getHumanDuration();
    }

    if (confProdProcessLine.getDefStockLocationAsFormula()) {
      stockLocation =
          (StockLocation)
              configuratorService.computeFormula(
                  confProdProcessLine.getStockLocationFormula(), attributes);
    } else {
      stockLocation = confProdProcessLine.getStockLocation();
    }

    prodProcessLine.setName(name);
    prodProcessLine.setPriority(priority);
    prodProcessLine.setWorkCenter(workCenter);
    prodProcessLine.setWorkCenterTypeSelect(confProdProcessLine.getWorkCenterTypeSelect());
    prodProcessLine.setWorkCenterGroup(confProdProcessLine.getWorkCenterGroup());
    prodProcessLine.setOutsourcing(confProdProcessLine.getOutsourcing());
    prodProcessLine.setStockLocation(stockLocation);
    prodProcessLine.setDescription(description);
    prodProcessLine.setMinCapacityPerCycle(minCapacityPerCycle);
    prodProcessLine.setMaxCapacityPerCycle(maxCapacityPerCycle);
    prodProcessLine.setDurationPerCycle(durationPerCycle);
    prodProcessLine.setHumanDuration(humanDuration);
    prodProcessLine.setDurationPerCycleDecimal(
        BigDecimal.valueOf(prodProcessLine.getDurationPerCycle())
            .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP));
    prodProcessLine.setHumanDurationDecimal(
        BigDecimal.valueOf(prodProcessLine.getHumanDuration())
            .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP));

    if (isConsProOnOperation) {
      List<ConfiguratorProdProduct> confProdProductLines =
          confProdProcessLine.getConfiguratorProdProductList();
      if (CollectionUtils.isNotEmpty(confProdProductLines)) {
        for (ConfiguratorProdProduct confProdProduct : confProdProductLines) {
          ProdProduct generatedProdProduct =
              confProdProductService.generateProdProduct(confProdProduct, attributes);
          if (generatedProdProduct != null) {
            prodProcessLine.addToConsumeProdProductListItem(generatedProdProduct);
          }
        }
      }
    }

    configuratorService.fixRelationalFields(prodProcessLine);

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
          String.format(
              I18n.get(
                  ProductionExceptionMessage.CONFIGURATOR_PROD_PROCESS_LINE_INCONSISTENT_CONDITION),
              confProdProcessLine.getId()));
    }

    return (boolean) computedConditions;
  }
}
