package com.jinhe.tss.portal.service;

import java.util.List;

import com.jinhe.tss.framework.component.log.Logable;
import com.jinhe.tss.portal.PortalConstants;
import com.jinhe.tss.portal.entity.Navigator;
import com.jinhe.tss.um.permission.filter.PermissionTag;
 
public interface INavigatorService {

	/**
     * 获取所有菜单
	 * @return
	 */
	@PermissionTag(
			operation = PortalConstants.NAVIGATOR_VIEW_OPERRATION, 
			resourceType = PortalConstants.NAVIGATOR_RESOURCE_TYPE)
	List<?> getAllNavigator();
	
	/**
     * 新建一个菜单或菜单项
	 * @param entity
	 * @return
	 */
	@Logable(operateObject="门户导航栏", operateInfo=" 新建/修改了 ${returnVal} 节点 ")
	Navigator saveNavigator(Navigator entity);
	
	/**
     * 删除一个菜单或者菜单项
	 * @param id
	 */
	@Logable(operateObject="门户导航栏", operateInfo=" 删除了 ID为 ${args[0]} 的 节点 ")
	void deleteNavigator(Long id);
	
	/**
     * 停/启用一个菜单或者菜单项
	 * @param id
	 * @param disabled
	 */
	@Logable(operateObject="门户导航栏", operateInfo="<#if args[1]=1>停用<#else>启用</#if>了(ID: ${args[0]})节点 ")
	void disable(Long id,Integer disabled);
	
	/**
     * 获取一个菜单或者菜单项
	 * @param id
	 * @return
	 */
	Navigator getNavigator(Long id);
	
	/**
     * 菜单项排序
	 * @param id
	 * @param targetId
	 * @param direction
	 */
	void sort(Long id, Long targetId, int direction);

    /**
     * 获取一个门户的所有菜单
     * @param id
     * @return
     */
    List<?> getNavigatorsByPortal(Long id);

    /**
     * 移动菜单项
     * @param id
     * @param targetId
     */
    void moveNavigator(Long id, Long targetId);

    /**
     * 生成一个菜单下所有菜单项集合的XML格式数据。
     * 注：本方法主要供门户取菜单调用，比较频繁，需要对其进行缓存，然后修改菜单的时候刷新该缓存。
     * 另外因为菜单是授权的，不同用户看到的菜单有可能不一样，所以菜单缓存只针对匿名用户进行缓存。
     * 
     * @param id
     * @return
     */
    String getNavigatorXML(Long id);
}
