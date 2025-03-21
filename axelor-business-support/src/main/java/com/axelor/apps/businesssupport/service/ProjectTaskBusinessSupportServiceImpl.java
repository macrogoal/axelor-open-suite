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
package com.axelor.apps.businesssupport.service;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.FrequencyRepository;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.FrequencyService;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businessproject.service.projecttask.ProjectTaskBusinessProjectServiceImpl;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.TaskTemplate;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.project.db.repo.TaskStatusProgressByCategoryRepository;
import com.axelor.apps.project.service.ProjectTimeUnitService;
import com.axelor.apps.project.service.TaskStatusToolService;
import com.axelor.apps.project.service.TaskTemplateService;
import com.axelor.apps.project.service.app.AppProjectService;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProjectTaskBusinessSupportServiceImpl extends ProjectTaskBusinessProjectServiceImpl {

  @Inject
  public ProjectTaskBusinessSupportServiceImpl(
      ProjectTaskRepository projectTaskRepo,
      FrequencyRepository frequencyRepo,
      FrequencyService frequencyService,
      AppBaseService appBaseService,
      ProjectRepository projectRepository,
      AppProjectService appProjectService,
      TaskStatusToolService taskStatusToolService,
      TaskStatusProgressByCategoryRepository taskStatusProgressByCategoryRepository,
      PriceListLineRepository priceListLineRepo,
      PriceListService priceListService,
      PartnerPriceListService partnerPriceListService,
      ProductCompanyService productCompanyService,
      TimesheetLineRepository timesheetLineRepository,
      ProjectTimeUnitService projectTimeUnitService,
      TaskTemplateService taskTemplateService) {
    super(
        projectTaskRepo,
        frequencyRepo,
        frequencyService,
        appBaseService,
        projectRepository,
        appProjectService,
        taskStatusToolService,
        taskStatusProgressByCategoryRepository,
        priceListLineRepo,
        priceListService,
        partnerPriceListService,
        productCompanyService,
        timesheetLineRepository,
        projectTimeUnitService,
        taskTemplateService);
  }

  @Override
  protected void setModuleFields(
      ProjectTask projectTask, LocalDate date, ProjectTask newProjectTask) {
    super.setModuleFields(projectTask, date, newProjectTask);

    // Module 'business support' fields
    newProjectTask.setAssignment(ProjectTaskRepository.ASSIGNMENT_PROVIDER);
  }

  @Override
  protected void updateModuleFields(ProjectTask projectTask, ProjectTask nextProjectTask) {
    super.updateModuleFields(projectTask, nextProjectTask);

    // Module 'business support' fields
    nextProjectTask.setAssignment(ProjectTaskRepository.ASSIGNMENT_PROVIDER);
    nextProjectTask.setIsPrivate(projectTask.getIsPrivate());
  }

  @Override
  public ProjectTask create(
      TaskTemplate template, Project project, LocalDateTime date, BigDecimal qty)
      throws AxelorException {

    ProjectTask task = super.create(template, project, date, qty);
    task.setInternalDescription(template.getInternalDescription());

    return task;
  }
}
