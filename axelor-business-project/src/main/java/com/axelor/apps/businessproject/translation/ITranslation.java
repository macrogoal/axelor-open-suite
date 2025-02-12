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
package com.axelor.apps.businessproject.translation;

public interface ITranslation {

  public static final String JOB_COSTING_APP_NAME = /*$$(*/ "value:Job costing"; /*)*/

  public static final String PROJECT_STATUS_CANCELED = /*$$(*/ "value:Canceled"; /*)*/

  public static final String PROJECT_STATUS_DONE_PAID = /*$$(*/ "value:Done paid"; /*)*/

  public static final String EXPENSE_LINE_CREATION_WITH_PROJECT = /*$$(*/
      "Expense line successfully created. The project is configured as billable, so the charge has been marked as billable by default."; /*)*/
  public static final String PROJECT_TASK_FOLLOW_UP_VALUES_TOO_HIGH = /*$$(*/
      "These following tasks have follow-up percentages above 1000%% or remaining amount over 10000 %s. <br>" /*)*/;

  public static final String REPORTING_VALUES_FOR_PROJECT = /*$$(*/
      "Reporting values for project with id %s." /*)*/;
}
