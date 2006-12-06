/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/workplace/commons/CmsLock.java,v $
 * Date   : $Date: 2006/12/06 16:11:12 $
 * Version: $Revision: 1.16.4.11 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Mananagement System
 *
 * Copyright (c) 2005 Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software GmbH, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.workplace.commons;

import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.jsp.CmsJspActionElement;
import org.opencms.lock.CmsLockFilter;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.security.CmsPermissionSet;
import org.opencms.util.CmsStringUtil;
import org.opencms.workplace.CmsDialog;
import org.opencms.workplace.CmsDialogSelector;
import org.opencms.workplace.CmsMultiDialog;
import org.opencms.workplace.CmsWorkplace;
import org.opencms.workplace.CmsWorkplaceSettings;
import org.opencms.workplace.I_CmsDialogHandler;
import org.opencms.workplace.list.CmsListExplorerColumn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;

import org.apache.commons.logging.Log;

/**
 * Creates the dialogs for locking, unlocking or steal lock operations on a resource.<p> 
 *
 * The following files use this class:
 * <ul>
 * <li>/commons/lock_standard.jsp
 * <li>/commons/lockchange_standard.jsp
 * <li>/commons/unlock_standard.jsp
 * <li>/commons/locks.jsp
 * </ul>
 * <p>
 * 
 * @author  Andreas Zahner 
 * 
 * @version $Revision: 1.16.4.11 $ 
 * 
 * @since 6.0.0 
 */
public class CmsLock extends CmsMultiDialog implements I_CmsDialogHandler {

    /** Value for the action: confirmed. */
    public static final int ACTION_SUBMIT_NOCONFIRMATION = 200;

    /** Request parameter value for the action: submit form without user interaction. */
    public static final String DIALOG_SUBMIT_NOCONFIRMATION = "submitnoconfirmation";

    /** The dialog type: lock a resource. */
    public static final String DIALOG_TYPE_LOCK = "lock";
    /** The dialog type: Steal a lock. */
    public static final String DIALOG_TYPE_LOCKCHANGE = "lockchange";
    /** The dialog type: locked subresources. */
    public static final String DIALOG_TYPE_LOCKS = "locks";
    /** The dialog type: unlock a resource. */
    public static final String DIALOG_TYPE_UNLOCK = "unlock";

    /** Request parameter name for the project id. */
    public static final String PARAM_PROJECT_ID = "projectid";
    /** Request parameter name for the publishsiblings parameter. */
    public static final String PARAM_PUBLISHSIBLINGS = "publishsiblings";

    /** Request parameter name for the source dialog uri. */
    public static final String PARAM_SOURCE_DIALOG = "sourcedialog";
    /** Request parameter name for the subresources parameter. */
    public static final String PARAM_SUBRESOURCES = "subresources";

    /** Type of the operation which is performed: lock resource. */
    public static final int TYPE_LOCK = 1;
    /** Type of the operation which is performed: steal a lock. */
    public static final int TYPE_LOCKCHANGE = 2;
    /** Type of the operation which is performed: locked subresources. */
    public static final int TYPE_LOCKS = 4;
    /** Type of the operation which is performed: unlock resource. */
    public static final int TYPE_UNLOCK = 3;

    /** The lock dialog URI. */
    public static final String URI_LOCK_DIALOG = PATH_DIALOGS + "lock_standard.jsp";
    /** The steal lock dialog URI. */
    public static final String URI_LOCKCHANGE_DIALOG = PATH_DIALOGS + "lockchange_standard.jsp";
    /** The locks dialog URI. */
    public static final String URI_LOCKS_DIALOG = PATH_DIALOGS + "locks.jsp";
    /** The unlock dialog URI. */
    public static final String URI_UNLOCK_DIALOG = PATH_DIALOGS + "unlock_standard.jsp";

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsLock.class);

    /** the filter to get all blocking locks. */
    private CmsLockFilter m_blockingFilter;

    /** the nunmber of blocking locked resources. */
    private int m_blockingLocks = -1;

    /** The list of locked resources.  */
    private List m_lockedResources;

    /** the filter to get all non blocking locks. */
    private CmsLockFilter m_nonBlockingFilter;

    /** The project id parameter value. */
    private String m_paramProjectid;

    /**
     * Default constructor needed for dialog handler implementation.<p>
     */
    public CmsLock() {

        super(null);
    }

    /**
     * Public constructor.<p>
     * 
     * @param jsp an initialized JSP action element
     */
    public CmsLock(CmsJspActionElement jsp) {

        super(jsp);
    }

    /**
     * Public constructor with JSP variables.<p>
     * 
     * @param context the JSP page context
     * @param req the JSP request
     * @param res the JSP response
     */
    public CmsLock(PageContext context, HttpServletRequest req, HttpServletResponse res) {

        this(new CmsJspActionElement(context, req, res));
    }

    /**
     * Determines if the resource should be locked, unlocked or if the lock should be stolen.<p>
     * 
     * @param cms the CmsObject
     * @return the dialog action: lock, change lock (steal) or unlock
     */
    public static int getDialogAction(CmsObject cms) {

        String fileName = CmsResource.getName(cms.getRequestContext().getUri());
        if (fileName == null) {
            // file name could not be determined, return "see locked subresources" action
            return TYPE_LOCKS;
        } else if (fileName.equalsIgnoreCase("lock.jsp")) {
            // a "lock" action is requested
            return TYPE_LOCK;
        } else if (fileName.indexOf("change") != -1) {
            // a "steal lock" action is requested
            return TYPE_LOCKCHANGE;
        } else if (fileName.indexOf("unlock") != -1) {
            // an "unlock" action is requested
            return TYPE_UNLOCK;
        } else {
            // an "see locked subresources" action is requested
            return TYPE_LOCKS;
        }
    }

    /**
     * Performs the lock/unlock operation, will be called by the JSP page.<p>
     * 
     * @throws JspException if problems including sub-elements occur
     */
    public void actionToggleLock() throws JspException {

        // save initialized instance of this class in request attribute for included sub-elements
        getJsp().getRequest().setAttribute(SESSION_WORKPLACE_CLASS, this);

        try {
            if (performDialogOperation()) {
                // if no exception is caused and "true" is returned the lock/unlock operation was successful          
                actionCloseDialog();
            } else {
                // "false" returned, display "please wait" screen
                getJsp().include(FILE_DIALOG_SCREEN_WAIT);
            }
        } catch (Throwable e) {
            // exception occured, show error dialog
            includeErrorpage(this, e);
        }
    }

    /**
     * Returns the html code to build the dialogs default confirmation message js.<p>
     * 
     * @return html code
     */
    public String buildDefaultConfirmationJS() {

        StringBuffer html = new StringBuffer(512);
        html.append("<script type='text/javascript'><!--\n");
        html.append("function setConfirmationMessage(locks, blockinglocks) {\n");
        html.append("\tvar confMsg = document.getElementById('conf-msg');\n");
        html.append("\tif (locks > -1) {\n");
        if (!getSettings().getUserSettings().getDialogShowLock() && (CmsLock.getDialogAction(getCms()) != CmsLock.TYPE_LOCKS)) {
            // auto commit if lock dialog disabled
            html.append("\t\tif (blockinglocks == 0) {\n");
            html.append("\t\t\tsubmitAction('");
            html.append(CmsDialog.DIALOG_OK);
            html.append("', null, 'main');\n");
            html.append("\t\t\tdocument.forms['main'].submit();\n");
            html.append("\t\t\treturn;\n");
            html.append("\t\t}\n");
        }
        html.append("\t\tdocument.getElementById('lock-body-id').className = '';\n");
        html.append("\t\tif (locks > '0') {\n");
        html.append("\t\t\tshowAjaxReportContent();\n");
        html.append("\t\t\tconfMsg.innerHTML = '");
        html.append(getConfirmationMessage(false));
        html.append("';\n");
        html.append("\t\t} else {\n");
        html.append("\t\t\tshowAjaxOk();\n");
        html.append("\t\t\tconfMsg.innerHTML = '");
        html.append(getConfirmationMessage(true));
        html.append("';\n");
        html.append("\t\t}\n");
        html.append("\t} else {\n");
        html.append("\t\tconfMsg.innerHTML = '");
        html.append(key(org.opencms.workplace.Messages.GUI_AJAX_REPORT_WAIT_0));
        html.append("';\n");
        html.append("\t}\n");
        html.append("}\n");
        html.append("// -->\n");
        html.append("</script>\n");
        return html.toString();
    }

    /**
     * Returns the html code to include the needed js code.<p>
     * 
     * @return html code
     */
    public String buildIncludeJs() {

        StringBuffer html = new StringBuffer(512);
        html.append("<script type='text/javascript' src='");
        html.append(CmsWorkplace.getSkinUri());
        html.append("commons/ajax.js'></script>\n");
        html.append("<script type='text/javascript' src='");
        html.append(CmsWorkplace.getSkinUri());
        html.append("editors/xmlcontent/help.js'></script>\n");
        html.append("<script type='text/javascript' src='");
        html.append(CmsWorkplace.getSkinUri());
        html.append("admin/javascript/general.js'></script>\n");
        html.append("<script type='text/javascript' src='");
        html.append(CmsWorkplace.getSkinUri());
        html.append("admin/javascript/list.js'></script>\n");
        html.append("<script type='text/javascript'><!--\n");
        html.append("function showAjaxOk() {\n");
        html.append("\tdocument.getElementById('ajaxreport-img').src = '");
        html.append(CmsWorkplace.getSkinUri());
        html.append("commons/ok.png';\n");
        html.append("\tdocument.getElementById('ajaxreport-txt').innerHTML = '");
        html.append(key(Messages.GUI_OPERATION_NO_LOCKS_0));
        html.append("';\n");
        html.append("}\n");
        html.append("var ajaxReportContent = '';");
        html.append("function showAjaxReportContent() {\n");
        html.append("\tif (ajaxReportContent != '') {\n");
        html.append("\t\tdocument.getElementById('ajaxreport').innerHTML = ajaxReportContent;\n");
        html.append("\t}\n");
        html.append("}\n");
        html.append("function doReportUpdate(msg, state) {\n");
        html.append("\tvar img = state + '.png';\n");
        html.append("\tvar txt = '';\n");
        html.append("\tvar locks = -1;\n");
        html.append("\tvar blockinglocks = -1;\n");
        html.append("\tvar elem = document.getElementById('ajaxreport');\n");
        html.append("\tif (state != 'ok') {\n");
        html.append("\t\tif (state != 'wait') {\n");
        html.append("\t\t\tdocument.getElementById('lock-body-id').className = '';\n");
        html.append("\t\t}\n");
        html.append("\t\tvar img = state + '.png';\n");
        html.append("\t\tvar txt = msg;\n");
        html.append("\t\tif (state == 'fatal') {\n");
        html.append("\t\t\timg = 'error.png';\n");
        html.append("\t\t\ttxt = '");
        html.append(key(org.opencms.workplace.Messages.GUI_AJAX_REPORT_GIVEUP_0));
        html.append("';\n");
        html.append("\t\t} else if (state == 'wait') {\n");
        html.append("\t\t\timg = 'wait.gif';\n");
        html.append("\t\t\ttxt = '");
        html.append(key(org.opencms.workplace.Messages.GUI_AJAX_REPORT_WAIT_0));
        html.append("'\n");
        html.append("\t\t} else if (state == 'error') {\n");
        html.append("\t\t\ttxt = '");
        html.append(key(org.opencms.workplace.Messages.GUI_AJAX_REPORT_ERROR_0));
        html.append("' + msg;\n");
        html.append("\t\t}\n");
        html.append("\t\tdocument.getElementById('ajaxreport-img').src = '");
        html.append(CmsWorkplace.getSkinUri());
        html.append("commons/' + img;\n");
        html.append("\t\tdocument.getElementById('ajaxreport-txt').innerHTML = txt;\n");
        html.append("\t} else {\n");
        html.append("\t\telem.innerHTML = elem.innerHTML + msg.substring(0, 120);\n");
        html.append("\t\tajaxReportContent = msg;\n");
        html.append("\t}\n");
        html.append("\tif (txt != '') {\n");
        html.append("\t}\n");
        html.append("\tif (state == 'ok') {\n");
        html.append("\t\tlocks = document.forms['main'].locks.value;\n");
        html.append("\t\tblockinglocks = document.forms['main'].blockinglocks.value;\n");
        html.append("\t}\n");
        html.append("\tsetConfirmationMessage(locks, blockinglocks);\n");
        html.append("}\n");
        html.append("// -->\n");
        html.append("</script>\n");
        return html.toString();
    }

    /**
     * Returns the html code to build the lock request.<p>
     * 
     * @return html code
     */
    public String buildLockRequest() {

        return buildLockRequest(0);
    }

    /**
     * Returns the html code to build the lock request.<p>
     * 
     * @param hiddenTimeout the maximal number of millis the dialog will be hidden
     * 
     * @return html code
     */
    public String buildLockRequest(int hiddenTimeout) {

        StringBuffer html = new StringBuffer(512);
        html.append("<script type='text/javascript'><!--\n");
        html.append("makeRequest('");
        html.append(getJsp().link("/system/workplace/commons/report-locks.jsp"));
        html.append("?");
        html.append(CmsMultiDialog.PARAM_RESOURCELIST);
        html.append("=");
        html.append(getParamResourcelist());
        html.append("&");
        html.append(CmsDialog.PARAM_RESOURCE);
        html.append("=");
        html.append(getParamResource());
        html.append("', 'doReportUpdate');\n");
        html.append("function showLockDialog() {\n");
        html.append("\tdocument.getElementById('lock-body-id').className = '';\n");
        html.append("}\n");
        html.append("setTimeout('showLockDialog()', " + hiddenTimeout + ");\n");
        html.append("// -->\n");
        html.append("</script>\n");
        return html.toString();
    }

    /**
     * Returns the report of all locked subresources.<p>
     * 
     * @return the report of all locked subresources
     * 
     * @throws JspException if dialog actions fail
     * @throws IOException in case of errros forwarding to the required result page
     * @throws ServletException in case of errros forwarding to the required result page
     */
    public String buildReport() throws JspException, ServletException, IOException {

        CmsLockedResourcesList list = new CmsLockedResourcesList(
            getJsp(),
            getLockedResources(),
            CmsResource.getParentFolder((String)getResourceList().get(0)));
        list.actionDialog();
        list.getList().setBoxed(false);

        StringBuffer result = new StringBuffer(512);
        result.append("<input type='hidden' name='locks' value='");
        result.append(getLockedResources().size()).append("'>\n");
        result.append("<input type='hidden' name='blockinglocks' value='");
        result.append(getBlockingLockedResources().size()).append("'>\n");
        result.append(CmsStringUtil.padLeft("", 120 - result.length()));
        result.append(CmsListExplorerColumn.getExplorerStyleDef());
        result.append("<div style='height:150px; overflow: auto;'>\n");
        result.append(list.getList().listHtml());
        result.append("</div>\n");
        return result.toString();
    }

    /**
     * Builds the necessary button row.<p>
     * 
     * @return the button row 
     */
    public String dialogButtons() {

        if (CmsLock.getDialogAction(getCms()) != CmsLock.TYPE_LOCKS) {
            return dialogButtonsOkCancel();
        } else {
            return dialogButtonsClose();
        }
    }

    /**
     * Returns the filter to get all blocking locks.<p>
     *
     * @return the filter to get all blocking locks
     */
    public CmsLockFilter getBlockingFilter() {

        if (m_blockingFilter == null) {
            m_blockingFilter = CmsLockFilter.FILTER_ALL;
            m_blockingFilter = m_blockingFilter.filterNotLockableByUser(getCms().getRequestContext().currentUser());
        }
        return m_blockingFilter;
    }

    /**
     * Returns the number of blocking locks.<p>
     *
     * @return the number of  blocking locks
     */
    public int getBlockingLocks() {

        if (m_blockingLocks == -1) {
            // to initialize the blocking locks flag
            getLockedResources();
        }
        return m_blockingLocks;
    }

    /**
     * Returns the confirmation message.<p>
     * 
     * @param state if <code>true</code> everything is ok
     * 
     * @return the confirmation message
     */
    public String getConfirmationMessage(boolean state) {

        if (getDialogAction(getCms()) == TYPE_LOCKS) {
            return "";
        }
        if (state) {
            if (isMultiOperation()) {
                return key(Messages.GUI_LOCK_MULTI_LOCK_CONFIRMATION_0);
            } else {
                return key(Messages.GUI_LOCK_CONFIRMATION_0);
            }
        }
        switch (getDialogAction(getCms())) {
            case TYPE_LOCK:
                if (isMultiOperation()) {
                    return key(Messages.GUI_LOCK_MULTI_INFO_LOCKEDSUBRESOURCES_0);
                } else {
                    return key(Messages.GUI_LOCK_INFO_LOCKEDSUBRESOURCES_0);
                }
            case TYPE_LOCKCHANGE:
                return key(Messages.GUI_LOCK_CHANGE_CONFIRMATION_0);
            case TYPE_UNLOCK:
                if (isMultiOperation()) {
                    return key(Messages.GUI_LOCK_MULTI_UNLOCK_CONFIRMATION_0);
                } else {
                    return key(Messages.GUI_LOCK_UNLOCK_CONFIRMATION_0);
                }
            case TYPE_LOCKS:
            default:
                return "";
        }
    }

    /**
     * @see org.opencms.workplace.I_CmsDialogHandler#getDialogHandler()
     */
    public String getDialogHandler() {

        return CmsDialogSelector.DIALOG_LOCK;
    }

    /**
     * @see org.opencms.workplace.I_CmsDialogHandler#getDialogUri(java.lang.String, CmsJspActionElement)
     */
    public String getDialogUri(String resource, CmsJspActionElement jsp) {

        switch (getDialogAction(jsp.getCmsObject())) {
            case TYPE_LOCK:
                return URI_LOCK_DIALOG;
            case TYPE_LOCKCHANGE:
                return URI_LOCKCHANGE_DIALOG;
            case TYPE_UNLOCK:
                return URI_UNLOCK_DIALOG;
            case TYPE_LOCKS:
            default:
                return URI_LOCKS_DIALOG;
        }
    }

    /**
     * Returns all the locked Resources.<p>
     *
     * @return all the locked Resources
     */
    public List getLockedResources() {

        if (m_lockedResources == null) {
            // collect my locked resources
            Set lockedResources = new HashSet();
            Iterator i = getResourceList().iterator();
            while (i.hasNext()) {
                String resName = (String)i.next();
                try {
                    lockedResources.addAll(getCms().getLockedResources(resName, getNonBlockingFilter()));
                } catch (CmsException e) {
                    // error reading a resource, should usually never happen
                    if (LOG.isErrorEnabled()) {
                        LOG.error(e);
                    }
                }
            }
            m_lockedResources = new ArrayList(lockedResources);
            // collect strangers locked resources
            m_lockedResources.addAll(getBlockingLockedResources());
            Collections.sort(m_lockedResources);
        }
        return m_lockedResources;
    }

    /**
     * Returns the filter to get all non blocking locks.<p>
     *
     * @return the filter to get all non blocking locks
     */
    public CmsLockFilter getNonBlockingFilter() {

        if (m_nonBlockingFilter == null) {
            m_nonBlockingFilter = CmsLockFilter.FILTER_ALL;
            m_nonBlockingFilter = m_nonBlockingFilter.filterLockableByUser(getCms().getRequestContext().currentUser());
            m_nonBlockingFilter = m_nonBlockingFilter.filterSharedExclusive();
        }
        return m_nonBlockingFilter;
    }

    /**
     * Returns the project id parameter value.<p>
     *
     * @return the project id parameter value
     */
    public String getParamProjectid() {

        return m_paramProjectid;
    }

    /**
     * Sets the filter to get all blocking locks.<p>
     *
     * @param blockingFilter the filter to set
     */
    public void setBlockingFilter(CmsLockFilter blockingFilter) {

        m_blockingFilter = blockingFilter;
        // reset blocking locks count
        m_blockingLocks = -1;
        // reset locked resources
        m_lockedResources = null;
    }

    /**
     * Sets the filter to get all non blocking locks.<p>
     *
     * @param nonBlockingFilter the filter to set
     */
    public void setNonBlockingFilter(CmsLockFilter nonBlockingFilter) {

        m_nonBlockingFilter = nonBlockingFilter;
        // reset locked resources
        m_lockedResources = null;
    }

    /**
     * Sets the project id parameter value.<p>
     *
     * @param projectid the project id parameter value to set
     */
    public void setParamProjectid(String projectid) {

        m_paramProjectid = projectid;
    }

    /**
     * Determines whether to show the lock dialog depending on the users settings and the dilaog type.<p>
     * 
     * In case of locking a folder, a confirmation dialog is needed if any sub resources are already locked.<p>
     * 
     * @return true if dialogs should be shown, otherwise false
     */
    public boolean showConfirmation() {

        boolean showConfirmation = getSettings().getUserSettings().getDialogShowLock();
        if (DIALOG_TYPE_LOCK.equals(getParamDialogtype())) {
            // in case of locking resources, check if there are locked sub resources in the selected folder(s)
            showConfirmation = showConfirmation || (getLockedResources().size() > 0);
        }
        return showConfirmation;
    }

    /**
     * @see org.opencms.workplace.CmsWorkplace#initWorkplaceRequestValues(org.opencms.workplace.CmsWorkplaceSettings, javax.servlet.http.HttpServletRequest)
     */
    protected void initWorkplaceRequestValues(CmsWorkplaceSettings settings, HttpServletRequest request) {

        // fill the parameter values in the get/set methods
        fillParamValues(request);

        // set the action for the JSP switch 
        if (DIALOG_CONFIRMED.equals(getParamAction())) {
            setAction(ACTION_CONFIRMED);
        } else if (DIALOG_CANCEL.equals(getParamAction())) {
            setAction(ACTION_CANCEL);
        } else if (DIALOG_WAIT.equals(getParamAction())) {
            setAction(ACTION_WAIT);
        } else {
            switch (getDialogAction(getCms())) {
                case TYPE_LOCK:
                    setDialogTitle(Messages.GUI_LOCK_RESOURCE_1, Messages.GUI_LOCK_MULTI_LOCK_2);
                    setParamDialogtype(DIALOG_TYPE_LOCK);
                    // check the required permissions to lock/unlock
                    if (!checkResourcePermissions(CmsPermissionSet.ACCESS_WRITE, false)) {
                        // no write permissions for the resource, set cancel action to close dialog
                        setAction(ACTION_CANCEL);
                        return;
                    }
                    break;
                case TYPE_LOCKCHANGE:
                    setDialogTitle(Messages.GUI_LOCK_STEAL_1, Messages.GUI_LOCK_MULTI_STEAL_2);
                    setParamDialogtype(DIALOG_TYPE_UNLOCK);
                    break;
                case TYPE_UNLOCK:
                    setDialogTitle(Messages.GUI_LOCK_UNLOCK_1, Messages.GUI_LOCK_MULTI_UNLOCK_2);
                    setParamDialogtype(DIALOG_TYPE_UNLOCK);
                    break;
                case TYPE_LOCKS:
                default:
                    setDialogTitle(Messages.GUI_LOCK_LOCKS_1, Messages.GUI_LOCK_MULTI_LOCKS_2);
                    setParamDialogtype(DIALOG_TYPE_LOCKS);
            }
            // set action depending on user settings
            if ((getDialogAction(getCms()) == TYPE_LOCKS) || showConfirmation()) {
                // show confirmation dialog
                setAction(ACTION_DEFAULT);
            } else {
                // lock/unlock resource without confirmation
                setAction(ACTION_SUBMIT_NOCONFIRMATION);
            }
        }

        if (getParamResource() == null && getParamResourcelist() == null) {
            // this if in case of publish project
            setParamResource("/");
        }

    }

    /**
     * Performs the lock/unlock/steal lock operation.<p>
     * 
     * @return true, if the operation was performed, otherwise false
     * @throws CmsException if operation is not successful
     */
    protected boolean performDialogOperation() throws CmsException {

        //on multi resource operation display "please wait" screen
        if (isMultiOperation() && !DIALOG_WAIT.equals(getParamAction())) {
            return false;
        }
        // determine action to perform (lock, unlock, change lock)
        int dialogAction = getDialogAction(getCms());

        // now perform the operation on the resource(s)
        Iterator i = getResourceList().iterator();
        while (i.hasNext()) {
            String resName = (String)i.next();
            try {
                performSingleResourceOperation(resName, dialogAction);
            } catch (CmsException e) {
                // collect exceptions to create a detailed output
                addMultiOperationException(e);
            }
        }
        // generate the error message for exception
        String message;
        if (dialogAction == TYPE_LOCK) {
            message = Messages.ERR_LOCK_MULTI_0;
        } else {
            message = Messages.ERR_UNLOCK_MULTI_0;
        }
        checkMultiOperationException(Messages.get(), message);

        return true;
    }

    /**
     * Performs the lock state operation on a single resource.<p>
     * 
     * @param resourceName the resource name to perform the operation on
     * @param dialogAction the lock action: lock, unlock or change lock
     * @throws CmsException if the operation fails
     */
    protected void performSingleResourceOperation(String resourceName, int dialogAction) throws CmsException {

        // store original name to use for lock action 
        String originalResourceName = resourceName;
        CmsResource res = getCms().readResource(resourceName, CmsResourceFilter.ALL);
        if (res.isFolder() && !resourceName.endsWith("/")) {
            resourceName += "/";
        }
        org.opencms.lock.CmsLock lock = getCms().getLock(res);
        // perform action depending on dialog uri
        switch (dialogAction) {
            case TYPE_LOCKCHANGE:
            case TYPE_LOCK:
                if (lock.isNullLock()) {
                    getCms().lockResource(originalResourceName);
                } else if (!lock.isExclusiveOwnedBy(getCms().getRequestContext().currentUser())
                    || !lock.isInProject(getCms().getRequestContext().currentProject())) {
                    getCms().changeLock(resourceName);
                }
                break;
            case TYPE_UNLOCK:
            default:
                if (lock.isNullLock()) {
                    break;
                }
                if (lock.isOwnedBy(getCms().getRequestContext().currentUser())) {
                    getCms().unlockResource(resourceName);
                }
        }
    }

    /**
     * Returns locked resources that do not belong to the current user.<p>
     * 
     * @return the locked Resources
     */
    public Set getBlockingLockedResources() {

        Set blockingResources = new HashSet();
        Iterator i = getResourceList().iterator();
        while (i.hasNext()) {
            String resName = (String)i.next();
            try {
                blockingResources.addAll(getCms().getLockedResources(resName, getBlockingFilter()));
            } catch (CmsException e) {
                // error reading a resource, should usually never happen
                if (LOG.isErrorEnabled()) {
                    LOG.error(e);
                }
            }
        }
        m_blockingLocks = blockingResources.size();
        return blockingResources;
    }
}