/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2012 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.message;

/**
 * Standard message channel events
 * 
 * @author Craig Cavanaugh $Id: ChannelEvent.java 3051 2012-01-02 11:27:23Z ccavanaugh $
 */
public enum ChannelEvent {
    ACCOUNT_ADD,
    ACCOUNT_ADD_FAILED,
    ACCOUNT_MODIFY,
    ACCOUNT_MODIFY_FAILED,
    ACCOUNT_REMOVE,
    ACCOUNT_REMOVE_FAILED,
    ACCOUNT_SECURITY_ADD,
    ACCOUNT_SECURITY_ADD_FAILED,
    ACCOUNT_SECURITY_REMOVE,
    ACCOUNT_SECURITY_REMOVE_FAILED,
    ACCOUNT_VISIBILITY_CHANGE,
    ACCOUNT_VISIBILITY_CHANGE_FAILED,
    BUDGET_ADD,
    BUDGET_ADD_FAILED,
    BUDGET_GOAL_UPDATE,
    BUDGET_GOAL_UPDATE_FAILED,
    BUDGET_UPDATE,
    BUDGET_UPDATE_FAILED,
    BUDGET_REMOVE,
    CURRENCY_ADD,
    CURRENCY_ADD_FAILED,
    CURRENCY_MODIFY,
    CURRENCY_MODIFY_FAILED,
    CURRENCY_REMOVE,
    CURRENCY_REMOVE_FAILED,
    COMMODITY_HISTORY_ADD,
    COMMODITY_HISTORY_ADD_FAILED,
    COMMODITY_HISTORY_REMOVE,
    COMMODITY_HISTORY_REMOVE_FAILED,
    EXCHANGERATE_ADD,
    EXCHANGERATE_REMOVE,
    EXCHANGERATE_REMOVE_FAILED,
    REMINDER_ADD,
    REMINDER_ADD_FAILED,
    REMINDER_REMOVE,
    TRANSACTION_ADD,
    TRANSACTION_ADD_FAILED,
    TRANSACTION_REMOVE,
    TRANSACTION_REMOVE_FAILED,
    FILE_CLOSING,
    FILE_NOT_FOUND,
    FILE_IO_ERROR,
    FILE_LOAD_FAILED,
    FILE_LOAD_SUCCESS,
    FILE_NEW_SUCCESS,
    UI_RESTARTED, // UI has restarted
    UI_RESTARTING
    // UI will restart
}