package com.jinhe.dm.report;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.jinhe.dm.DMConstants;
import com.jinhe.dm.data.sqlquery.SQLExcutor;
import com.jinhe.dm.report.permission.ReportPermission;
import com.jinhe.dm.report.permission.ReportResource;
import com.jinhe.dm.report.timer.ReportJob;
import com.jinhe.tss.framework.component.param.Param;
import com.jinhe.tss.framework.component.param.ParamConstants;
import com.jinhe.tss.framework.component.param.ParamManager;
import com.jinhe.tss.framework.component.param.ParamService;
import com.jinhe.tss.framework.component.timer.SchedulerBean;
import com.jinhe.tss.framework.exception.BusinessException;
import com.jinhe.tss.framework.sso.Environment;
import com.jinhe.tss.framework.sso.IOperator;
import com.jinhe.tss.framework.web.dispaly.tree.LevelTreeParser;
import com.jinhe.tss.framework.web.dispaly.tree.StrictLevelTreeParser;
import com.jinhe.tss.framework.web.dispaly.tree.TreeEncoder;
import com.jinhe.tss.framework.web.dispaly.xform.XFormEncoder;
import com.jinhe.tss.framework.web.mvc.BaseActionSupport;
import com.jinhe.tss.um.helper.PasswordRule;
import com.jinhe.tss.um.permission.PermissionHelper;
import com.jinhe.tss.um.service.ILoginService;
import com.jinhe.tss.util.BeanUtil;
import com.jinhe.tss.util.DateUtil;
import com.jinhe.tss.util.EasyUtils;
import com.jinhe.tss.util.FileHelper;
import com.jinhe.tss.util.URLUtil;

@Controller
@RequestMapping("/auth/rp")
public class ReportAction extends BaseActionSupport {
    
    @Autowired private ReportService reportService;
    @Autowired private ILoginService loginService;
    
    @RequestMapping("/")
    public void getAllReport(HttpServletResponse response) {
	    checkPwdSecurity();
    	
        List<?> list = reportService.getAllReport();
        TreeEncoder treeEncoder = new TreeEncoder(list, new StrictLevelTreeParser(Report.DEFAULT_PARENT_ID));
        print("SourceTree", treeEncoder);
    }
    
    /**
     * 如果指定了分组，则只取该分组下的报表
     */
    @RequestMapping("/my")
    public void getCustomizeReports(HttpServletResponse response, Long groupId) {
	    checkPwdSecurity();
	    
	    List<Report> list;
	    if(groupId != null) {
	    	list = reportService.getReportsByGroup(groupId);
	    	for(Report temp : list) {
	    		if(groupId.equals(temp.getId())) {
	    			list.remove(temp);
	    			break;
	    		}
	    	}
	    } else {
	    	list = reportService.getAllReport();
	    }
	    
	    // 查出过去100天个人和整站访问Top10的报表名称
	    List<String> topSelf = getTops(true);
	    List<String> topX = getTops(false);
	    Long selfGroupId = -2L, topGroupId = -3L, newGroupId = -4L;
	    		
	    List<Report> result = new ArrayList<Report>();
	    if(topSelf.size() > 0) {
	    	result.add(new Report(selfGroupId, "您最近访问报表"));
	    	for(String temp : topSelf) {
	    		for(Report report : list) {
		    		if(temp.equals(report.getName()) && report.isActive() && !report.isGroup()) {
		        		result.add(cloneReport(selfGroupId, report));
		        		break;
		    		}
		    	}
	    	}
	    }
	    if(topX.size() > 0) {
	    	result.add(new Report(topGroupId, "近期热门报表"));
	    	for(String temp : topX) {
	    		for(Report report : list) {
		    		if(temp.equals(report.getName()) && report.isActive() && !report.isGroup()) {
		        		result.add(cloneReport(topGroupId, report));
		        		break;
		    		}
		    	}
	    	}
	    }
	    
	    result.add(new Report(newGroupId, "近期新出报表"));
	    List<Report> latest = new ArrayList<Report>();
    	for(Report report : list) {
    		if( !report.isActive() )  continue;
 
    		if( !report.isGroup() 
    				&& report.getCreateTime().after(DateUtil.subDays(DateUtil.today(), 10))
    				&& DMConstants.hasCNChar(report.getName())) {
    			
    			latest.add(cloneReport(newGroupId, report));
    		}
    		
    		result.add(report); // 此处将list里的所有report及分组放入到result里
    	}
    	Collections.sort(latest, new Comparator<Report>() {
            public int compare(Report r1, Report r2) {
                return r2.getId().intValue() - r1.getId().intValue();
            }
        });
    	result.addAll(latest);
       
        TreeEncoder treeEncoder = new TreeEncoder(result, new LevelTreeParser());
        treeEncoder.setNeedRootNode(false);
        print("SourceTree", treeEncoder);
    }

	private Report cloneReport(Long topGroupId, Report report) {
		Report clone = new Report();
		BeanUtil.copy(clone, report);
		clone.setParentId(topGroupId);
		return clone;
	}
 
    private List<String> getTops(boolean onlySelf) {
    	String sql = "select methodCnName name, count(*) value, max(l.accessTime) lastTime " +
	    		" from dm_access_log l " +
	    		" where l.accessTime >= ? " + (onlySelf ? " and l.userId = ?" : "") +
	    		" group by methodCnName " +
	    		" order by " + (onlySelf ? "lastTime" : "value")  + " desc";
	    SQLExcutor ex = new SQLExcutor(false);
	    Map<Integer, Object> params = new HashMap<Integer, Object>();
	    params.put(1, DateUtil.subDays(DateUtil.today(), 30));
	    if(onlySelf) {
	    	params.put(2, Environment.getUserId());
	    }
		ex.excuteQuery(sql, params , DMConstants.LOCAL_CONN_POOL);
	    
	    List<String> tops = new ArrayList<String>();
	    for( Map<String, Object> row : ex.result){
	    	if(tops.size() < 8) {
	    		String reportName = (String) row.get("name");
	    	    if(DMConstants.hasCNChar(reportName)) {
	    	    	tops.add(reportName);
	    	    }
	    	}
	    }
	    return tops;
    }

    // add 2014.12.17 检查用户的密码强度，太弱的话强制要求修改密码
	private void checkPwdSecurity() {
    	Object strengthLevel = null;
    	try {
    		Long operatorId = Environment.getUserId();
			IOperator operator = loginService.getOperatorDTOByID(operatorId);
			strengthLevel = operator.getAttributesMap().get("passwordStrength");
    	} catch(Exception e) {
    		// do nothing
     	}
    	if(strengthLevel != null && EasyUtils.obj2Int(strengthLevel) <= PasswordRule.LOW_LEVEL) {
			throw new BusinessException("您的密码过于简单，请点右上角【修改密码】菜单重置密码后，再进行访问！");
		}
	}
	
    @RequestMapping("/template")
    public void getReportTLs(HttpServletResponse response) {
    	StringBuffer sb = new StringBuffer("<actionSet>"); 
    	
    	// btr or wms 目录下
    	String rtd = DMConstants.getReportTLDir();
 		File reportTLDir = new File(URLUtil.getWebFileUrl(rtd).getPath());
		List<File> files = FileHelper.listFilesByTypeDeeply("html", reportTLDir);
		int index = 1;
 		for (File file : files) {
			String treeName = "../" + rtd.substring(8);
			File parentFile = file.getParentFile();
			if( !parentFile.equals(reportTLDir) ) {
				treeName +=  "/" + parentFile.getName();
			}
			treeName +=  "/" + file.getName();
			
			sb.append("<treeNode id=\"").append(index++).append("\" name=\"").append(treeName).append("\"/>");
		}
 		
 		// dm/template 下
 		File delfaultDir = new File(URLUtil.getWebFileUrl("modules/" + DMConstants.REPORT_TL_DIR_DEFAULT).getPath());
 		if( !delfaultDir.equals(reportTLDir) ) {
 			files = FileHelper.listFilesByTypeDeeply("html", delfaultDir);
 	 		for (File file : files) {
 				String treeName = "../dm/template/" + file.getName();
 				sb.append("<treeNode id=\"").append(index++).append("\" name=\"").append(treeName).append("\"/>");
 			}
 		}
		
        print("SourceTree", sb);
    }
    
    @RequestMapping("/groups")
    public void getAllReportGroups(HttpServletResponse response) {
        List<?> list = reportService.getAllReportGroups();
        TreeEncoder treeEncoder = new TreeEncoder(list, new StrictLevelTreeParser(Report.DEFAULT_PARENT_ID));
        treeEncoder.setNeedRootNode(true);
        print("SourceTree", treeEncoder);
    }
    
    @RequestMapping(value = "/detail/{type}")
    public void getReport(HttpServletRequest request, HttpServletResponse response, @PathVariable("type") int type) {
        String uri = null;
        if(Report.TYPE0 == type) {
            uri = "template/group_xform.xml";
        } else {
            uri = "template/report_xform.xml";
        }
        
        XFormEncoder xformEncoder;
        String reportIdValue = request.getParameter("reportId");
        
        if( reportIdValue == null) {
            Map<String, Object> map = new HashMap<String, Object>();
            
            String parentIdValue = request.getParameter("parentId"); 
            if("_root".equals(parentIdValue)) {
            	parentIdValue = null;
            }
            
            Long parentId = parentIdValue == null ? Report.DEFAULT_PARENT_ID : EasyUtils.obj2Long(parentIdValue);
            map.put("parentId", parentId);
            map.put("type", type);
            xformEncoder = new XFormEncoder(uri, map);
        } 
        else {
            Long reportId = EasyUtils.obj2Long(reportIdValue);
            Report report = reportService.getReport(reportId);
            xformEncoder = new XFormEncoder(uri, report);
        }
        
        if( Report.TYPE1 == type ) {
            try {
            	List<Param> datasources = ParamManager.getComboParam(DMConstants.DATASOURCE_LIST);
                xformEncoder.fixCombo("datasource", datasources);	
            } catch (Exception e) {
            }
        }
 
        print("SourceInfo", xformEncoder);
    }

    @RequestMapping(method = RequestMethod.POST)
    public void saveReport(HttpServletResponse response, Report report) {
        boolean isnew = (null == report.getId());
        reportService.saveReport(report);
        doAfterSave(isnew, report, "SourceTree");
    }
    
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void delete(HttpServletResponse response, @PathVariable("id") Long id) {
        reportService.delete(id);
        
        // 删除定时JOB，如果有的话
        String jobCode = "ReportJob-" + id;
		List<Param> jobParamItems = paramService.getParamsByParentCode(SchedulerBean.TIMER_PARAM_CODE);
		if(jobParamItems != null) {
			for(Param temp : jobParamItems) {
				if(jobCode.equals(temp.getUdf1())) {
					paramService.delete(temp.getId());
				}
			}
		}
		
        printSuccessMessage();
    }

    @RequestMapping(value = "/disable/{id}/{disabled}", method = RequestMethod.POST)
    public void startOrStop(HttpServletResponse response, 
            @PathVariable("id") Long id, @PathVariable("disabled") int disabled) {
        
        reportService.startOrStop(id, disabled);
        printSuccessMessage();
    }
 
    @RequestMapping(value = "/sort/{startId}/{targetId}/{direction}", method = RequestMethod.POST)
    public void sort(HttpServletResponse response, 
            @PathVariable("startId") Long startId, 
            @PathVariable("targetId") Long targetId, 
            @PathVariable("direction") int direction) {
        
        reportService.sort(startId, targetId, direction);
        printSuccessMessage();
        
    }

    @RequestMapping(value = "/copy/{reportId}/{groupId}", method = RequestMethod.POST)
    public void copy(HttpServletResponse response, 
            @PathVariable("reportId") Long reportId, @PathVariable("groupId") Long groupId) {
        
        List<?> result = reportService.copy(reportId, groupId);
        TreeEncoder encoder = new TreeEncoder(result, new LevelTreeParser());
        encoder.setNeedRootNode(false);
        print("SourceTree", encoder);
    }

    @RequestMapping(value = "/move/{reportId}/{groupId}", method = RequestMethod.POST)
    public void move(HttpServletResponse response, 
            @PathVariable("reportId") Long reportId, @PathVariable("groupId") Long groupId) {
        
        reportService.move(reportId, groupId);
        printSuccessMessage();
    }
    
	@RequestMapping("/operations/{resourceId}")
    public void getOperations(HttpServletResponse response, @PathVariable("resourceId") Long resourceId) {
        List<String> list = PermissionHelper.getInstance().getOperationsByResource(resourceId,
                        ReportPermission.class.getName(), ReportResource.class);

        print("Operation", EasyUtils.list2Str(list));
    }
	
	
	@Autowired ParamService paramService;
	
	@RequestMapping(value = "/schedule", method = RequestMethod.POST)
    public void saveJobParam(HttpServletResponse response, Long reportId, String configVal) {
		Param jobParam = paramService.getParam(SchedulerBean.TIMER_PARAM_CODE);
		if(jobParam == null) {
			jobParam = new Param();
			jobParam.setCode(SchedulerBean.TIMER_PARAM_CODE);
			jobParam.setName("定时配置");
			jobParam.setParentId(ParamConstants.DEFAULT_PARENT_ID);
			jobParam.setType(ParamConstants.NORMAL_PARAM_TYPE);
			jobParam.setModality(ParamConstants.COMBO_PARAM_MODE);
	        paramService.saveParam(jobParam);
    	}
		
		Param jobParamItem = null;
		String jobCode = "ReportJob-" + reportId;
		List<Param> jobParamItems = paramService.getParamsByParentCode(SchedulerBean.TIMER_PARAM_CODE);
		for(Param temp : jobParamItems) {
			if(jobCode.equals(temp.getUdf1())) {
				jobParamItem = temp;
				break;
			}
		}
		if(jobParamItem == null) {
			jobParamItem = new Param();
			jobParamItem.setText(reportService.getReport(reportId).getName() + "-" + reportId);
			jobParamItem.setUdf1(jobCode);
			jobParamItem.setParentId(jobParam.getId());
			jobParamItem.setType(ParamConstants.ITEM_PARAM_TYPE);
			jobParamItem.setModality(jobParam.getModality());
		}
		jobParamItem.setValue(ReportJob.class.getName() + " | " + configVal);
        paramService.saveParam(jobParamItem);
        
        printSuccessMessage();
    }

	@RequestMapping(value = "/schedule", method = RequestMethod.GET)
	@ResponseBody
    public Object[] getJobParam(HttpServletResponse response, Long reportId) {
		String jobCode = "ReportJob-" + reportId;
		List<Param> jobParamItems = paramService.getParamsByParentCode(SchedulerBean.TIMER_PARAM_CODE);
		if(jobParamItems != null) {
			for(Param temp : jobParamItems) {
				if(jobCode.equals(temp.getUdf1())) {
					String value = temp.getValue();
					return EasyUtils.split(value, "|");
				}
			}
		}
		return null;
    }
}
