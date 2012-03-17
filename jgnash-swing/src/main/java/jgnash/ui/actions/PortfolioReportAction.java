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
package jgnash.ui.actions;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;

import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.message.Message;
import jgnash.message.MessageBus;
import jgnash.message.MessageChannel;
import jgnash.ui.report.compiled.PortfolioReport;
import jgnash.ui.util.builder.Action;

/**
 * UI Action to run the portfolio report
 *
 * @author Craig Cavanaugh
 * @version $Id: PortfolioReportAction.java 3051 2012-01-02 11:27:23Z ccavanaugh $
 */
@Action("report-portfolio-command")
public class PortfolioReportAction extends AbstractEnabledAction {

    private static final long serialVersionUID = 1L;


    public PortfolioReportAction() {
        super();
        MessageBus.getInstance().registerListener(this, MessageChannel.ACCOUNT);
        updateEnabledState();
    }

    @Override
    public void messagePosted(final Message event) {

        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                switch (event.getEvent()) {
                    case FILE_CLOSING:
                        setEnabled(false);
                        break;
                    case FILE_NEW_SUCCESS:
                    case FILE_LOAD_SUCCESS:
                    case ACCOUNT_REMOVE:
                    case ACCOUNT_ADD:
                        updateEnabledState();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void updateEnabledState() {

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                final boolean enabled = hasInvestmentAccount();

                EventQueue.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        setEnabled(enabled);
                    }
                });
            }
        });

        thread.setPriority(Thread.MIN_PRIORITY);

        thread.start();
    }

    private boolean hasInvestmentAccount() {
        Engine e = EngineFactory.getEngine(EngineFactory.DEFAULT);

        if (e != null) {
            return e.getInvestmentAccountList().size() > 0;
        }
        
        return false;

    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        new PortfolioReport().showReport();
    }
}