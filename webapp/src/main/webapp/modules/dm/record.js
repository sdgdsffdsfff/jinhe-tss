/* 后台响应数据节点名称 */
XML_SOURCE_TREE = "SourceTree";
XML_RECORD_DATA = "RecordData";
XML_SOURCE_INFO = "SourceInfo";

PAGESIZE = 100;

/* XMLHTTP请求地址汇总 */
URL_SOURCE_TREE    = AUTH_PATH + "rc/all";
URL_GROUPS_TREE    = AUTH_PATH + "rc/groups";
URL_SOURCE_DETAIL  = AUTH_PATH + "rc/detail";
URL_SAVE_SOURCE    = AUTH_PATH + "rc";
URL_DELETE_SOURCE  = AUTH_PATH + "rc/";
URL_SORT_SOURCE    = AUTH_PATH + "rc/sort/";
URL_MOVE_SOURCE    = AUTH_PATH + "rc/move/";
URL_GET_OPERATION  = AUTH_PATH + "rc/operations/";  // {id}

if(IS_TEST) {
	URL_SOURCE_TREE    = "data/record_tree.xml?";
	URL_GROUPS_TREE    = "data/groups_tree.xml?";
	URL_SOURCE_DETAIL  = "data/record_detail.xml?";
	URL_SAVE_SOURCE    = "data/_success.xml?";
	URL_DELETE_SOURCE  = "data/_success.xml?";
	URL_SORT_SOURCE    = "data/_success.xml?";
	URL_MOVE_SOURCE    = "data/_success.xml?";
	URL_GET_OPERATION  = "data/_operation.xml?";
}

$(function() {
	init();
});	

/* 页面初始化 */
function init() {
	initMenus();
	initEvents();

	loadInitData();
}

/* 菜单初始化 */
function initMenus() {
	/* 树菜单初始化  */
	ICON = "images/"
	var item1 = {
		label:"录入数据",
		callback:showRecord,
		icon: ICON + "icon_edit.gif",
		visible:function() {return isRecord() && getOperation("1,2,3,4,5");}
	}
	var item21 = {
		label:"修改分组",
		callback: function() {
			loadRecordDetail(false, "0");
		},
		visible:function() { return isRecordGroup() && getOperation("2"); }
	}
	var item22 = {
		label:"修改录入表",
		callback: function() {
			loadRecordDetail(false, "1");
		},
		visible:function() { return isRecord() && getOperation("2"); }
	}
	var item3 = {
		label:"新增录入表",
		callback: function() {
			loadRecordDetail(true, "1");
		},
		visible:function() {return (isRecordGroup() || isTreeRoot()) && getOperation("2");}
	}
	var item4 = {
		label:"新增分组",
		callback: function() {
			loadRecordDetail(true, "0");
		},
		icon: ICON + "icon_folder_new.gif",
		visible:function() {return (isRecordGroup() || isTreeRoot()) && getOperation("2");}
	}
	var item5 = {
		label:"删除",
		callback:deleteRecord,
		icon: ICON + "icon_del.gif",
		visible:function() {return !isTreeRoot() && getOperation("3");}
	}
	var item6 = {
		label:"移动到",
		callback:moveRecord,
		icon: ICON + "icon_move.gif",
		visible:function() {return !isTreeRoot() && getOperation("2");}
	}

	var menu = new $.Menu();
	menu.addItem(item1);
	menu.addSeparator();
	menu.addItem(item3);
	menu.addItem(item4);
	menu.addItem(item21);
	menu.addItem(item22);
	menu.addItem(item6);
	menu.addItem(item5);
	menu.addSeparator();
	menu.addItem(createPermissionMenuItem("D2"));
	
	$1("tree").contextmenu = menu;
}

function isRecordGroup() {
	return "0" == getTreeAttribute("type");
}

function isRecord() {
	return !isTreeRoot() && !isRecordGroup();
}

function loadInitData() {
	var onresult = function() {
		var tree = $.T("tree", this.getNodeValue(XML_SOURCE_TREE));

		tree.onTreeNodeDoubleClick = function(ev) {
			var treeNode = getActiveTreeNode();
			getTreeOperation(treeNode, function(_operation) {            
				if( isRecord() && getOperation("1,2,3,4,5") ) {
					showRecord();
				}
				if( isRecordGroup() && getOperation("2") ) {
					loadRecordDetail(false, "0");
				}
			});
		}
		tree.onTreeNodeRightClick = function(ev) {
			onTreeNodeRightClick(ev, true);
		}
		tree.onTreeNodeMoved = function(ev) {
			sortTreeNode(URL_SORT_SOURCE, ev);
		}
	}

	$.ajax({url : URL_SOURCE_TREE, onresult : onresult});
}

function loadRecordDetail(isCreate, type) { 
	var treeNode = $.T("tree").getActiveTreeNode();
	var treeNodeID = treeNode.id;
	type = type || treeNode.getAttribute("type") ;
	
	$("#chatFrame").hide();
	$("#recordFormDiv").show(true);
	
	var params = {};
	if( isCreate ) {
		params["parentId"] = treeNodeID; // 新增
	} else {
		params["recordId"] = treeNodeID; // 修改					
	}

	$.ajax({
		url : URL_SOURCE_DETAIL + "/" + type,
		params : params,
		onresult : function() { 
			var sourceInfoNode = this.getNodeValue(XML_SOURCE_INFO);
			$.cache.XmlDatas[treeNodeID] = sourceInfoNode;
			
			var xform = $.F("recordForm", sourceInfoNode);
		
			// 设置保存/关闭按钮操作
			$1("closeRecordForm").onclick = function() {
				closeRecordFormDiv();
			}
			$1("sourceSave").onclick = function() {
				saveRecord(treeNodeID);
			}
		},
		onexception : function() { 
			closeRecordFormDiv();
		}
	});
}

function saveRecord(treeNodeID) {
	var xform = $.F("recordForm");	
	if( !xform.checkForm() ) return;

	var request = new $.HttpRequest();
	request.url = URL_SAVE_SOURCE;

	var sourceInfoNode = $.cache.XmlDatas[treeNodeID];
	var dataNode = sourceInfoNode.querySelector("data");
	request.setFormContent(dataNode);

	syncButton([$1("sourceSave")], request); // 同步按钮状态

	request.onresult = function() { // 新增结果返回              
		var xmlNode = this.getResponseXML().querySelector("treeNode");
		appendTreeNode(treeNodeID, xmlNode);

		closeRecordFormDiv();
		delete $.cache.XmlDatas[treeNodeID];
	}
	request.onsuccess = function() { // 更新
		modifyTreeNode(treeNodeID, "name", xform.getData("name"));
		modifyTreeNode(treeNodeID, "define", xform.getData("define"));
		modifyTreeNode(treeNodeID, "customizePage", xform.getData("customizePage"));
		
		closeRecordFormDiv();
		delete $.cache.XmlDatas[treeNodeID];
	}
	request.send();
}

function closeRecordFormDiv() {
	$("#recordDefinesDiv").hide();
	$("#recordFormDiv").hide();
}

function deleteRecord()  { delTreeNode(URL_DELETE_SOURCE); }

function moveRecord() {
	var tree = $.T("tree");
	var treeNode = tree.getActiveTreeNode();
	var id  = treeNode.id;
    var pId = treeNode.parent.id;

    var params = {id:id, parentID: pId};
    popupTree(URL_GROUPS_TREE, "SourceTree", params, function(target) {
    	var targetId = (target.id == '_root' ? '0' : target.id);
        moveTreeNode(tree, id, targetId, URL_MOVE_SOURCE);
    });
}		

function showRecord() {
	var treeNode = getActiveTreeNode();

	globalValiable.id = treeNode.id;
	globalValiable.name  = treeNode.name;
	globalValiable._operation = treeNode.getAttribute("_operation");

	closePalette(); // 关闭左栏
	$("#recordFormDiv").hide();
    closeDefine();

	var customizePage  = (treeNode.getAttribute("customizePage") || "").trim(); 
	customizePage = customizePage || 'recorder.html';
	$("#chatFrame").show().attr("src", customizePage);
}  

// -------------------------------------------------   配置数据录入表   ------------------------------------------------
function configDefine() {
	var rform = $.F("recordForm");
	var _define = $.parseJSON(rform.getData("define")) || [];

	var fieldNodes = [];
	_define.each(function(index, item){
		var paramNode = {"id": index, "name": item.label, "value": JSON.stringify(item)};
		fieldNodes.push(paramNode); 
	});

	var treeData = [{"id": "_root", "name": "字段列表", "children": fieldNodes}];
	var fieldTree = $.T("fieldTree", treeData);	
	initFieldTreeMenus();

	fieldTree.onTreeNodeActived = function(event) {
		if(fieldTree.getActiveTreeNode().id != '-1') {
			editFieldConfig();
		}
	}
	fieldTree.onTreeNodeRightClick = function(event) {
		if(fieldTree.getActiveTreeNode().id != '-1') {
			editFieldConfig();
		}
		fieldTree.el.contextmenu.show(event.clientX, event.clientY);
	}
	fieldTree.onTreeNodeMoved = function(event) {
		event.ownTree.sortTreeNode(event.dragNode, event.destNode);
	}

	// 默认选中第一个参数，如果没有则清空表单
	var fieldNodeIds = fieldTree.getAllNodeIds();
	if(fieldNodeIds.length > 0) {
		fieldTree.setActiveTreeNode(fieldNodeIds[0]);
		editFieldConfig();
	}
	else {
		RECORD_PARAM_FIELDS.each(function(i, field){
    		var fieldEl = $1("_" + field);
    		fieldEl.value = '';
    	});
	}

	$("#recordDefinesDiv").show(true).center();
 
}

function initFieldTreeMenus() {
	var fieldTree = $.T("fieldTree");
    var item1 = {
        label:"删除字段",
        icon: ICON + "icon_del.gif",
        callback:function() {
            fieldTree.removeActiveNode();
        },
        visible: function() { 
        	return fieldTree.getActiveTreeNode().id != '_root';
        }
    }
    var item2 = {
        label:"新建字段",
        callback:function() {
			var id = $.now();
			var newNode = {'id': id, 'name': '新增字段', 'value': '{"label":"新增字段"}'};
			fieldTree.addTreeNode(newNode);
			fieldTree.setActiveTreeNode(id);
			editFieldConfig();
        },
        visible: function() { 
        	return fieldTree.getActiveTreeNode().id === '_root'; 
        }
    }
 
    var menu1 = new $.Menu();
    menu1.addItem(item2);
	menu1.addItem(item1);
    fieldTree.el.contextmenu = menu1;
}

function closeDefine() {
	$("#recordDefinesDiv").hide();
}

var RECORD_PARAM_FIELDS = ['label', 'code', 'type', 'nullable', 'defaultValue', 'isparam', 'checkReg', 'errorMsg', 'width', 'height', 'options', 'multiple', 'onchange'];

function editFieldConfig() {
	var fieldTree = $.T("fieldTree");
	var activeNode = fieldTree.getActiveTreeNode();
    var valuesMap = $.parseJSON(fieldTree.getActiveTreeNodeAttr("value")) || {};
    RECORD_PARAM_FIELDS.each(function(i, field){
    	var fieldEl = $1("_" + field);
    	var fieldValue = valuesMap[field] || '';

		if(field === 'type') {
			fieldValue = fieldValue.toLowerCase();
			if(fieldValue == "date" || fieldValue == "datetime") {
				$("#selectRelation").css("display", "none");
			}
			else {
				$("#selectRelation").css("display", "block");
			}
		}

		if( field === 'options' ) {
			if(fieldValue.codes) {
				fieldValue = fieldValue.codes + ',' + fieldValue.names;
			}
			else if( valuesMap['jsonUrl'] ) {
				fieldValue = valuesMap['jsonUrl']
			}
		}

	    if(field === 'width' || field === 'height') { 
	    	fieldValue = fieldValue ? fieldValue.replace('px', '') : (field === 'width' ? 250 : 18);
	    	$('#_' + field + '_').html(fieldValue);
			
			fieldEl.onchange = function() {
				$('#_' + field + '_').html(this.value);
			}
		}
		fieldEl.value = fieldValue;

    	fieldEl.onblur = function() {
    		var newValue = fieldEl.value;
			if( $.isNullOrEmpty(newValue) ) {
				if(field === 'label') {
					return $(fieldEl).notice("新建字段名称不能为空");
				}
				if(field === 'code') {
					return $(fieldEl).notice("新建字段CODE不能为空");
				}
				delete valuesMap[field];
			}
			else {
				valuesMap[field] = newValue;
			}			

    		if(field === 'label') {
				activeNode.attrs[field] = newValue;
				activeNode.li.a.title = newValue;
				$(activeNode.li.a).html(newValue);
    		}
    		if(field === 'options' && newValue) {
    			newValue = newValue.replace(/，/ig, ',') // 替换中文逗号
    			if(newValue.indexOf('|') < 0 && newValue.indexOf(',') < 0) {
    				delete valuesMap['options'];
    				valuesMap['jsonUrl'] = newValue;
    			}
    			else {
	    			delete valuesMap['jsonUrl'];
					var tmpArray = newValue.split(",");
					var names = (tmpArray.length > 1 ? tmpArray[1] : tmpArray[0]);
	    			valuesMap['options'] = {"codes": tmpArray[0], "names": names};
	    		}
    		} 

    		if(field === 'type') {
    			if(newValue == "date" || newValue == "datetime" || newValue == "hidden") {
    				if(newValue != "hidden") {
    					$1("_defaultValue").setAttribute("placeholder", "日期类型示例：today-3");
    				}
					$("#selectRelation").css("display", "none");
    			}
				else {
					$("#selectRelation").css("display", "block");
				}
    		}
    		activeNode.setAttribute("value", JSON.stringify(valuesMap));
    	}
    });
}

function saveDefine() {
	var result = [];
	var fieldNodes = $.T("fieldTree").getAllNodes();
	fieldNodes.each(function(i, node){
		var valuesMap = $.parseJSON(node.attrs["value"] || '{}');
		result.push( valuesMap );
	});

	var formatResult = JSON.stringify(result).replace(/\"/g, "'").replace(/\{'label/g, "\n  {'label");
	$.F("recordForm").updateDataExternal("define", formatResult.replace(/\}]/g, "}\n]"));
}