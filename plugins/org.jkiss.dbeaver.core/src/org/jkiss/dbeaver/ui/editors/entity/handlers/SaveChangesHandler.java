/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2017 Serge Rider (serge@jkiss.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.editors.entity.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISaveablePart;
import org.eclipse.ui.ISaveablePart2;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.jkiss.dbeaver.DBeaverPreferences;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.core.DBeaverUI;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.DBRRunnableWithProgress;
import org.jkiss.dbeaver.ui.dialogs.ConfirmationDialog;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.dbeaver.ui.editors.entity.EntityEditor;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.InvocationTargetException;


public class SaveChangesHandler extends AbstractHandler
{
    private static final Log log = Log.getLog(SaveChangesHandler.class);

    public static boolean validateAndSave(DBRProgressMonitor monitor, ISaveablePart saveable)
    {
        if (!saveable.isDirty()) {
            return true;
        }
        SaveRunner saveRunner = new SaveRunner(monitor, saveable);
        DBeaverUI.syncExec(saveRunner);
        return saveRunner.getResult();
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        final EntityEditor editor = RuntimeUtils.getObjectAdapter(HandlerUtil.getActiveEditor(event), EntityEditor.class);
        if (editor != null) {
            try {
                DBeaverUI.runInProgressService(new DBRRunnableWithProgress() {
                    @Override
                    public void run(DBRProgressMonitor monitor) throws InvocationTargetException, InterruptedException
                    {
                        validateAndSave(monitor, editor);
                    }
                });
            } catch (InvocationTargetException e) {
                //UIUtils.showErrorDialog();
            } catch (InterruptedException e) {
                // skip
            }
        }
        return null;
    }

    private static class SaveRunner implements Runnable {
        private final DBRProgressMonitor monitor;
        private final ISaveablePart saveable;
        private boolean result;

        private SaveRunner(DBRProgressMonitor monitor, ISaveablePart saveable)
        {
            this.monitor = monitor;
            this.saveable = saveable;
        }

        public boolean getResult()
        {
            return result;
        }

        @Override
        public void run()
        {
            int choice = -1;
            if (saveable instanceof ISaveablePart2) {
                choice = ((ISaveablePart2) saveable).promptToSaveOnClose();
            }
            if (choice == -1 || choice == ISaveablePart2.DEFAULT) {
                Shell shell;
                String saveableName;
                if (saveable instanceof IWorkbenchPart) {
                    shell = ((IWorkbenchPart) saveable).getSite().getShell();
                    saveableName = ((IWorkbenchPart) saveable).getTitle();
                } else {
                    shell = DBeaverUI.getActiveWorkbenchShell();
                    saveableName = CommonUtils.toString(saveable);
                }
                int confirmResult = ConfirmationDialog.showConfirmDialog(
                    shell,
                    DBeaverPreferences.CONFIRM_EDITOR_CLOSE,
                    ConfirmationDialog.QUESTION_WITH_CANCEL,
                    saveableName);
                switch (confirmResult) {
                    case IDialogConstants.YES_ID:
                        choice = ISaveablePart2.YES;
                        break;
                    case IDialogConstants.NO_ID:
                        choice = ISaveablePart2.NO;
                        break;
                    default:
                        choice = ISaveablePart2.CANCEL;
                        break;
                }
            }
            switch (choice) {
                case ISaveablePart2.YES: //yes
                    saveable.doSave(RuntimeUtils.getNestedMonitor(monitor));
                    if (saveable.isDirty()) {
                        log.debug("Saveable '" + saveable + "' is still dirty after save");
                    }
                    result = true;
                    break;
                case ISaveablePart2.NO: //no
                    result = true;
                    break;
                case ISaveablePart2.CANCEL: //cancel
                default:
                    result = false;
                    break;
            }
        }
    }
}