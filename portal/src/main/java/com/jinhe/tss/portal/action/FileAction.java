package com.jinhe.tss.portal.action;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.jinhe.tss.framework.exception.BusinessException;
import com.jinhe.tss.framework.web.mvc.BaseActionSupport;
import com.jinhe.tss.portal.PortalConstants;
import com.jinhe.tss.portal.entity.Component;
import com.jinhe.tss.portal.helper.ComponentHelper;
import com.jinhe.tss.util.EasyUtils;
import com.jinhe.tss.util.FileHelper;
import com.jinhe.tss.util.URLUtil;

/**
 * 管理门户、页面、布局器、修饰器、布局器等的附件资源。
 */
@Controller
@RequestMapping("/auth/portal/file")
public class FileAction extends BaseActionSupport {
 
	@RequestMapping("/list")
    public void listAvailableFiles(HttpServletResponse response, HttpServletRequest request) {
        String code = request.getParameter("code");
        String type = request.getParameter("type"); // 判断是何种类型的资源管理
        String filter = request.getParameter("filter");
        String contextPath = request.getParameter("contextPath");
        if( type != null && !Arrays.asList(Component.TYPE_NAMES).contains(type) ) {
            throw new BusinessException("指定文件类型有误。");
        }
        
        StringBuffer sb = new StringBuffer("<actionSet title=\"\" openednodeid=\"r1.1\">"); 
        
        // 如果访问的是子目录，则提供目录上翻的按钮
        if( contextPath != null){
            sb.append("<treeNode id=\"-1\" name=\"..\" icon=\"../framework/images/folder.gif\" />"); 
        } 
        else {
        	if( type != null) {
        		contextPath = PortalConstants.MODEL_DIR + type + "/" + code;
        	}
        	else { // 默认取门户资源目录根节点
        		contextPath = PortalConstants.PORTAL_MODEL_DIR + "/" + code;
        	}
        }
        
        // 根据type值找根目录
        String absolutePath = URLUtil.getWebFileUrl(contextPath).getPath();
		File baseDir = new File(absolutePath); 
                
        filter = getFilter(filter);
        List<String> files= sortFile(baseDir, FileHelper.listFilesByType(filter, baseDir));
        
        int i = 0;
        for( String fileName : files ){
            sb.append("<treeNode id=\"").append(i++).append("\" name=\"").append(fileName);
            
            boolean isFolder = FileHelper.isFolder(baseDir, fileName);
            if(isFolder) {
                sb.append("\" isFolder=\"").append("1");
            }
            sb.append("\" icon=\"../framework/images/" + (isFolder ? "folder.gif" : "file.gif") + "\"/>");
        }
        sb.append("</actionSet>");      
        
        print(new String[] {"ResourceTree", "ContextPath"}, new Object[] {sb, contextPath});
    }
    
    /* 将子文件夹、文件进行归类，文件夹在前 */
    private List<String> sortFile(File baseDir, List<String> files){
        List<String> dirs = new ArrayList<String>();
        for(Iterator<String> it = files.iterator(); it.hasNext();){
            String fileName = (String) it.next();
            if(FileHelper.isFolder(baseDir, fileName)){
                dirs.add(fileName);
                it.remove();
            }
        }
        dirs.addAll(files);
        return dirs;
    }
    
    /**
     * 下载
     */
    @RequestMapping(method = RequestMethod.GET)
    public void download(HttpServletResponse response, HttpServletRequest request) {
    	String contextPath = request.getParameter("contextPath");
    	String fileNames = request.getParameter("fileNames");
    	String folderNames = request.getParameter("folderNames");
    	
        if(fileNames == null && folderNames == null) return;
        
        // 建立临时文件夹存放要下载的所有文件
        contextPath = getContextPath(contextPath);
        File tempDir = new File(contextPath + "temp"); 
        if ( !tempDir.exists() ) {
            tempDir.mkdir();
        }
        
        List<File> files = new ArrayList<File>();
        if(fileNames != null) {
        	String[] fNames = fileNames.split(",");
            for ( String fileName : fNames ) {
                File file = new File(contextPath + fileName);
                if (file.exists()) {
                	files.add(file);
                }
            }
        }
        
        if(folderNames != null) {
        	String[] fNames = folderNames.split(",");
            for ( String folderName : fNames ) {
                File folder = new File(contextPath + folderName);
                files.addAll(FileHelper.listFilesDeeply(folder));
            }
        }
        
        for(File file : files) {
        	FileHelper.copyFile(tempDir, file, true, false);  // 拷贝文件至临时文件夹
        }
        String zipFilePath = FileHelper.exportZip(contextPath, tempDir); // 将临时文件夹里的文件打包成zip文件
        FileHelper.deleteFile(tempDir); // 删除临时文件夹
        
        ComponentHelper.downloadFileByHttp(zipFilePath, "download.zip"); // 下载zip包
    }
    
    /**
     * 删除文件（文件夹）
     */
    @RequestMapping(method = RequestMethod.DELETE)
    public void deleteFile(HttpServletResponse response, HttpServletRequest request) {
    	String contextPath = request.getParameter("contextPath");
    	String fileNames   = request.getParameter("fileNames");
    	String folderNames = request.getParameter("folderNames");
    	
        List<String> pathList = new ArrayList<String>();
        if(fileNames != null) {
            pathList.addAll(Arrays.asList(fileNames.split(",")));
        }
        if(folderNames != null) {
            pathList.addAll(Arrays.asList(folderNames.split(",")));
        }
        
        contextPath = getContextPath(contextPath);
        for(String fileOrDir : pathList){
            if( EasyUtils.isNullOrEmpty(fileOrDir)) continue;
            try {
                FileHelper.deleteFile(new File(contextPath + fileOrDir));
            } catch (Exception e) {
                throw new BusinessException(fileOrDir + "文件删除失败，可能文件正在使用中！", e);
            }
        }
 
        print("script", "parent.loadFileTree();");
    }
    
    /**
     * 重命名文件（文件夹）
     */
    @RequestMapping(method = RequestMethod.PUT)
    public void renameFile(HttpServletResponse response, HttpServletRequest request) {
    	String contextPath = request.getParameter("contextPath");
    	String fileName    = request.getParameter("fileName");
    	String newFileName = request.getParameter("newFileName");
    	
    	contextPath = getContextPath(contextPath);
        File newFile = new File(contextPath + newFileName);
        if(newFile.exists()) {
            throw new BusinessException("同名文件(夹)已经存在，重命名失败！");
        }
        
        File file = new File(contextPath + fileName);
        if( !file.renameTo(newFile) ) {
        	throw new BusinessException("重命名失败，可能文件正在使用中！");
        }
        print("script", "parent.loadFileTree();");
    }
    
    /**
     * 新建文件夹 
     */
    @RequestMapping(method = RequestMethod.POST)
    public void addDir(HttpServletResponse response, HttpServletRequest request){
    	String contextPath = request.getParameter("contextPath");
    	String newFileName = request.getParameter("newFileName");
    	
    	contextPath = getContextPath(contextPath);
        FileHelper.createDir(contextPath + newFileName);
        print("script", "window.parent.loadFileTree();");
    }
 
    public static String getContextPath(String contextPath) {
        String modelDir = URLUtil.getWebFileUrl("").getPath();
        return modelDir + "/" + contextPath + "/";
    }
 
    private String getFilter(String filter) {
        if(filter == null || filter.length() == 0 || "*.*".equals(filter)){
            return "";
        }
        if(filter.startsWith("*")){
            return filter.substring(1);
        }
        return filter;
    }
}

