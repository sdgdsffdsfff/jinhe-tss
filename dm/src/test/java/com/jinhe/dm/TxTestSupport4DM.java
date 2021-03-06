package com.jinhe.dm;

import java.util.List;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.transaction.TransactionConfiguration;

import com.jinhe.tss.framework.Global;
import com.jinhe.tss.framework.component.param.Param;
import com.jinhe.tss.framework.component.param.ParamConstants;
import com.jinhe.tss.framework.component.param.ParamService;
import com.jinhe.tss.framework.sso.IdentityCard;
import com.jinhe.tss.framework.sso.TokenUtil;
import com.jinhe.tss.framework.sso.context.Context;
import com.jinhe.tss.framework.test.IH2DBServer;
import com.jinhe.tss.framework.test.TestUtil;
import com.jinhe.tss.um.UMConstants;
import com.jinhe.tss.um.helper.dto.OperatorDTO;
import com.jinhe.tss.um.permission.PermissionHelper;
import com.jinhe.tss.um.permission.PermissionService;
import com.jinhe.tss.um.service.ILoginService;
import com.jinhe.tss.um.service.IResourceService;
import com.jinhe.tss.util.XMLDocUtil;

@ContextConfiguration(
        locations={
                "classpath:META-INF/dm-spring-test.xml",  
                "classpath:META-INF/framework-spring.xml",  
                "classpath:META-INF/um-spring.xml",
                "classpath:META-INF/dm-spring.xml",
                "classpath:META-INF/spring-mvc.xml"
              }, inheritLocations = false // 是否要继承父测试用例类中的 Spring 配置文件，默认为 true
      )
@TransactionConfiguration(defaultRollback = false) // 自动回滚设置为false，否则数据将不插进去
public abstract class TxTestSupport4DM extends AbstractTransactionalJUnit4SpringContextTests { 
 
	protected Logger log = Logger.getLogger(this.getClass());    
    
    @Autowired protected IH2DBServer dbserver;
    
    @Autowired protected ParamService paramService;
    @Autowired protected IResourceService resourceService;
    @Autowired protected ILoginService loginSerivce;
    @Autowired protected PermissionService permissionService;
    @Autowired protected PermissionHelper permissionHelper;
    
    protected MockHttpServletRequest request;
    protected MockHttpServletResponse response;
    
    @Before
    public void setUp() throws Exception {
        Global.setContext(super.applicationContext);
        Context.setResponse(response = new MockHttpServletResponse());
		Context.initRequestContext(request = new MockHttpServletRequest());
 
        // DB数据在一轮跑多个单元测试中初始化一次就够了。
        if( dbserver.isPrepareed() ) {
            return;
        }
        
        init();
        
        dbserver.setPrepareed(true);
    }
    
    protected String getDefaultSource(){
    	return "connectionpool";
    }
 
    @After
    public void tearDown() throws Exception {
        dbserver.stopServer();
    }
    
    /**
     * 初始化DM
     */
    protected void init() {
    	// 初始化数据库脚本
    	String sqlpath = TestUtil.getInitSQLDir();
    	log.info( " sql path : " + sqlpath);
        TestUtil.excuteSQL(sqlpath + "/framework");
        TestUtil.excuteSQL(sqlpath + "/um");
    	TestUtil.excuteSQL(sqlpath + "/dm");
    	
    	// 初始化虚拟登录用户信息
        login(UMConstants.ADMIN_USER_ID, UMConstants.ADMIN_USER_NAME);
        
        /* 初始化应用系统、资源、权限项 */
        Document doc = XMLDocUtil.createDocByAbsolutePath(TestUtil.getSQLDir() + "/dm-resource-config.xml");
        resourceService.setInitial(true);
        resourceService.applicationResourceRegister(doc, UMConstants.PLATFORM_SYSTEM_APP);
        resourceService.setInitial(false);
        
        if(paramService.getParam(DMConstants.DATASOURCE_LIST) == null) {
        	Param dlParam = addParamGroup(ParamConstants.DEFAULT_PARENT_ID, DMConstants.DATASOURCE_LIST, "数据源列表");
            addParamItem(dlParam.getId(), "connectionpool-1", "数据源1", ParamConstants.COMBO_PARAM_MODE);
        }
        if(paramService.getParam(DMConstants.DEFAULT_CONN_POOL) == null) {
            addParam(ParamConstants.DEFAULT_PARENT_ID, DMConstants.DEFAULT_CONN_POOL, "默认数据源", getDefaultSource());
        }
        if(paramService.getParam(DMConstants.TEMP_EXPORT_PATH) == null) {
			String tmpDir = System.getProperty("java.io.tmpdir") + "temp";
			log.info("临时文件导出目录：" + tmpDir);
            addParam(ParamConstants.DEFAULT_PARENT_ID, DMConstants.TEMP_EXPORT_PATH, "临时文件导出目录", tmpDir);
        }
    }
    
    protected void login(Long userId, String loginName) {
    	OperatorDTO loginUser = new OperatorDTO(userId, loginName);
    	String token = TokenUtil.createToken("1234567890", userId); 
        IdentityCard card = new IdentityCard(token, loginUser);
        Context.initIdentityInfo(card);
        
        // 获取登陆用户的权限（拥有的角色）并保存到用户权限（拥有的角色）对应表
        List<Object[]> userRoles = loginSerivce.getUserRolesAfterLogin(userId);
        permissionService.saveUserRolesAfterLogin(userRoles, userId);
    }
    
    /** 简单参数 */
    protected Param addParam(Long parentId, String code, String name, String value) {
        Param param = new Param();
        param.setCode(code);
        param.setName(name);
        param.setValue(value);
        param.setParentId(parentId);
        param.setType(ParamConstants.NORMAL_PARAM_TYPE);
        param.setModality(ParamConstants.SIMPLE_PARAM_MODE);
        paramService.saveParam(param);
        return param;
    }
    
    /** 下拉型参数 */
    protected Param addParamGroup(Long parentId, String code, String name) {
        Param param = new Param();
        param.setCode(code);
        param.setName(name);
        param.setParentId(parentId);
        param.setType(ParamConstants.NORMAL_PARAM_TYPE);
        param.setModality(ParamConstants.COMBO_PARAM_MODE);
        paramService.saveParam(param);
        return param;
    }

    /** 新建设参数项 */
    protected Param addParamItem(Long parentId, String value, String text, Integer mode) {
        Param param = new Param();
        param.setValue(value);
        param.setText(text);
        param.setParentId(parentId);
        param.setType(ParamConstants.ITEM_PARAM_TYPE);
        param.setModality(mode);
        paramService.saveParam(param);
        return param;
    }
}
