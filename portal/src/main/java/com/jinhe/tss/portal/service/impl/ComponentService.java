package com.jinhe.tss.portal.service.impl;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jinhe.tss.framework.exception.BusinessException;
import com.jinhe.tss.portal.PortalConstants;
import com.jinhe.tss.portal.dao.IComponentDao;
import com.jinhe.tss.portal.entity.Component;
import com.jinhe.tss.portal.service.IComponentService;
import com.jinhe.tss.util.FileHelper;
import com.jinhe.tss.util.URLUtil;

@Service("ComponentService")
public class ComponentService implements IComponentService {

    @Autowired private IComponentDao dao;

    public List<?> getAllComponentsAndGroups() {
        return dao.getEntities("from Component o order by o.decode ");
    }

    public List<?> getEnabledComponentsAndGroups(int type) {
        return dao.getEntities("from Component o where o.disabled <> 1 and o.type=? order by o.decode", type);
    }
 
    public Component saveComponent(Component obj) {
        if (obj.getId() == null) {
            obj.setCode(obj.getComponentType() + System.currentTimeMillis());
            
            Integer seqNo = dao.getNextSeqNo(obj.getParentId());
            obj.setSeqNo(seqNo);
        }
        return dao.save(obj);
    }
    
    public Component deleteComponent(Long id) {
        Component component = dao.getEntity(id);
        checkElementInUse(component);
        return dao.deleteComponent(component);
    }

    private void checkElementInUse(Component component) {
        if(PortalConstants.TRUE.equals(component.getIsDefault())) {
            throw new BusinessException("删除组件为默认的修饰器或布局器，删除失败！");
        }
        
        Component group = dao.getEntity(component.getParentId());
        String hql = null;
        switch (group.getType()) {
        case Component.PORTLET_TYPE:  // Portlet组
            hql = "from Structure t where t.definerId = ? and t.type = 3 ";
            break;
        case Component.LAYOUT_TYPE:   // 布局器组
            hql = "select t from Structure t, ThemeInfo ti where t.id = ti.id.structureId and ti.layoutId = ? ";
            break;
        case Component.DECORATOR_TYPE: // 修饰器组
            hql = "select t from Structure t, ThemeInfo ti where t.id = ti.id.structureId and ti.decoratorId = ? ";
            break;
        }     
 
        Long elementId = component.getId();
        if( dao.getEntities(hql, elementId).size() > 0) {
            throw new BusinessException("结点: 【ID为：" + elementId + " 名称为： " + component.getName() + "】 在使用中，删除失败！");
        }
    }
 
    public void disableComponent(Long id, Integer disabled) {
        Component component = getComponent(id);
        if(PortalConstants.TRUE.equals(component.getIsDefault())) {
            throw new BusinessException("停用组件为默认的修饰器或布局器，停用失败！");
        }
        
        if (!component.getDisabled().equals(disabled)) {
            component.setDisabled(disabled);
            dao.save(component);
        }
    }
 
    public Component getComponent(Long id) {
        return dao.getEntity(id);
    }
 
    public void sort(Long id, Long targetId, int direction) {
        dao.sort(id, targetId, direction);
    }

    public Component copyComponent(Long id, File path) {
        Component component = getComponent(id);
        return copyElement(component, path, PortalConstants.COPY_PREFIX);
    }
    
    private Component copyElement(Component component, File path, String prefix) {
        // 先取到拷贝原本element的资源文件目录
        File fileDir = FileHelper.findPathByName(path, component.getCode() + component.getId());
        
        dao.evict(component);
        component.setId(null);
        component.setName(prefix + component.getName());
        component.setIsDefault(PortalConstants.FALSE);
        component = saveComponent(component);
        
        if (fileDir != null) {
            FileHelper.copyFolder(fileDir.toString(), path + "/" + component.getCode() + component.getId());
        }
        return component;
    }
    
    public void setDecoratorAsDefault(Long decoratorId) {
        List<?> list = dao.getEntities("from Component o where o.type = ? and o.isDefault = 1", Component.DECORATOR_TYPE);
        for ( Object temp : list ) {
            Component decorator = (Component) temp;
            decorator.setIsDefault(PortalConstants.FALSE);
            dao.update(decorator);     
        }     
        
        Component decorator = dao.getEntity(decoratorId);
        decorator.setIsDefault(PortalConstants.TRUE);
        decorator.setDisabled(PortalConstants.FALSE); // 设置为默认布局器则启用该修饰器（不管有没有停用掉）
        dao.update(decorator);       
    }
    
    public void setLayoutAsDefault(Long layoutId) {
        List<?> list = dao.getEntities("from Component o where o.type = ? and o.isDefault = 1", Component.LAYOUT_TYPE);
        for ( Object temp : list ) {
            Component layout = (Component) temp;
            layout.setIsDefault(PortalConstants.FALSE);
            dao.update(layout);     
        }        
        
        Component layout = dao.getEntity(layoutId);
        layout.setIsDefault(PortalConstants.TRUE);
        layout.setDisabled(PortalConstants.FALSE); // 设置为默认布局器则启用该布局器（不管有没有停用掉）
        dao.update(layout);       
    }
    
    /*****************************************************************************************************************
     ************************************ 以下是对元素（修饰器/布局器/Portlet）组的操作 ************************************* 
     *****************************************************************************************************************/   
    public void moveTo(Long id, Long groupId) {
        Component component = dao.getEntity(id);
        component.setSeqNo(dao.getNextSeqNo(groupId));
        component.setParentId(groupId);
        
        dao.save(component);  
    }
    
    public void deleteComponentGroup(Long groupId) {
        // 检查组下是否有元素尚在使用中
        List<?> components = dao.getComponentsDeeply(groupId);
        for ( Object component : components ) {
            checkElementInUse( (Component) component );
        }
            
        Component entity = dao.getEntity( groupId );
        dao.deleteGroup(entity);
    }
 
    public List<?> getComponentGroups(Integer type){
        return dao.getEntities("from Component t where t.type = ? and isGroup = ? order by t.decode", type, true);
    }

    public Component copyTo(Long id, Long groupId) {
        Component group = dao.getEntity( groupId );
        Component component = dao.getEntity( id );
        
        // 如果目标节点和原父节点是同一个，则当"复制"操作处理，name前面加前缀
        String prefix = "";
        if(component.getParentId().equals(groupId)) {
            prefix = PortalConstants.COPY_PREFIX;
        }
        
        component.setParentId(groupId);
        
        URL resourceUri = URLUtil.getWebFileUrl(group.getResourceBaseDir());
        File path = new File(resourceUri.getPath());
        copyElement(component, path, prefix);
        
        return component;
    }
}
