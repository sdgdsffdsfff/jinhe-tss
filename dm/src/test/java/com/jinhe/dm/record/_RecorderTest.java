package com.jinhe.dm.record;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jinhe.dm.DMConstants;
import com.jinhe.dm.TxTestSupport4DM;
import com.jinhe.dm.record.file.CreateAttach;
import com.jinhe.dm.record.file.RecordAttach;
import com.jinhe.tss.framework.component.log.LogService;
import com.jinhe.tss.framework.component.param.ParamConstants;
import com.jinhe.tss.framework.test.TestUtil;
import com.jinhe.tss.framework.web.servlet.AfterUpload;
import com.jinhe.tss.util.EasyUtils;
import com.jinhe.tss.util.FileHelper;

public class _RecorderTest extends TxTestSupport4DM {
	
	@Autowired LogService logService;
	@Autowired RecordService recordService;
	@Autowired _Recorder recorder;
	
	Long recordId;
	
	@Before
	public void setUp() throws Exception {
		super.setUp();
		
		String tblDefine = "[ {'label':'类型', 'code':'f1', 'type':'number', 'nullable':'false'}," +
        		"{'label':'名称', 'code':'f2', 'type':'string'}," +
        		"{'label':'时间', 'code':'f3', 'type':'datetime', 'nullable':'false'}]";
		
		Record record = new Record();
		record.setName("record-1-1");
		record.setType(1);
		record.setParentId(0L);
		
		record.setDatasource(DMConstants.LOCAL_CONN_POOL);
		record.setTable("x_tbl_12");
		record.setDefine(tblDefine);
		record.setNeedLog(ParamConstants.TRUE);
		
		record.setNeedFile(ParamConstants.TRUE);
		
		recordService.saveRecord(record);
		recordId = record.getId();
	}

	@Test
	public void test() {
		Assert.assertNotNull(recorder.getDefine(recordId));
		
		request.addParameter("f1", "10.9");
		request.addParameter("f2", "just test");
		
		// test 更新时必填字段为空
		try {
			recorder.create(request, response, recordId);
			Assert.fail("should throw exception but didn't.");
		} 
		catch(Exception e) { }
				
		request.addParameter("f3", "2015-04-05");
		recorder.create(request, response, recordId);
		recorder.update(request, response, recordId, 1);
		
		// test 更新时必填字段为空
		try {
			request.removeParameter("f3");
			recorder.update(request, response, recordId, 1);
			Assert.fail("should throw exception but didn't.");
		} 
		catch(Exception e) { }
		
		List<Map<String, Object>> result = recorder.getDB(recordId).select().result;
		Assert.assertTrue(result.size() == 1);
		
		request = new MockHttpServletRequest();
		request.addParameter("f2", "just test"); // test as query condition
		recorder.showAsGrid(request, response, recordId, 1);
		recorder.showAsJSON(request, recordId, 1);
		
		for(int i = 0; i < 17; i++) {
			request = new MockHttpServletRequest();
			request.addParameter("f1", "12.0");
			request.addParameter("f2", "i'm " + i);
			request.addParameter("f3", "2015-04-05");
			recorder.update(request, response, recordId, 1); // 多次修改，以生成日志
		}
		
		recorder.updateBatch(response, recordId, "1,2", "f1", "1212");
		result = recorder.getDB(recordId).select().result;
		Map<String, Object> recordItem = result.get(0);
		Assert.assertTrue(recordItem.get("f1").equals(1212.0d));
		
		// test upload record attach
		Long itemId = EasyUtils.obj2Long(recordItem.get("id"));
		uploadDocFile(recordId, itemId);
		List<?> attachList = recorder.getAttachList(recordId, itemId);
		Assert.assertTrue(attachList.size() == 1);
		
		RecordAttach ra = (RecordAttach) attachList.get(0);
		log.info(ra.toString());
		Assert.assertEquals("123.txt", ra.getName());
		Assert.assertEquals(recordId, ra.getRecordId());
		Assert.assertEquals(itemId, ra.getItemId());
		Assert.assertNotNull(ra.getSeqNo());
		Assert.assertEquals("txt", ra.getFileExt());
		Assert.assertNotNull(ra.getFileName());
		Assert.assertNotNull(ra.getUploadDate());
		Assert.assertTrue(ra.isOfficeDoc());
		Assert.assertFalse(ra.isImage());
		
		recorder.showAsGrid(request, response, recordId, 1);
		
		try {
			recorder.downloadAttach(new MockHttpServletResponse(), ra.getId());
		} catch (IOException e1) {
			Assert.assertTrue("下载附件出错。", true);
		}
		
		recorder.getAttachListXML(response, recordId, itemId);
		
		recorder.deleteAttach(response, ra.getId());
		
		attachList = recorder.getAttachList(recordId, itemId);
		Assert.assertTrue(attachList.size() == 0);
		
		// test delete record
		recorder.delete(response, recordId, 1);
		
		result = recorder.getDB(recordId).select().result;
		Assert.assertTrue(result.size() == 0);
		
		try { Thread.sleep(1000); } catch (Exception e) { } // 等待修改日志输出
		assertTrue(TestUtil.printLogs(logService) > 0);
		
		try {
			request = new MockHttpServletRequest();
			request.addParameter("f1", "12.12");
			request.addParameter("f2", "just test end");
			request.addParameter("f3", "2015-04-05");
			recorder.update(request, response, recordId, 1);
        	Assert.fail("should throw exception but didn't.");
        } catch (Exception e) {
        	Assert.assertTrue("修改出错，该记录不存在，可能已经被删除。", true);
        }
	}
	
	static String UPLOAD_PATH = TestUtil.getTempDir() + "/upload/record/";
	
	 // 上传附件
    private void uploadDocFile(Long recordId, Object itemId) {
    	AfterUpload upload = new CreateAttach();
    	
	    IMocksControl mocksControl =  EasyMock.createControl();
	    HttpServletRequest mockRequest = mocksControl.createMock(HttpServletRequest.class);
	    
	    EasyMock.expect(mockRequest.getParameter("recordId")).andReturn(recordId.toString());
	    EasyMock.expect(mockRequest.getParameter("itemId")).andReturn(itemId.toString());
	    EasyMock.expect(mockRequest.getParameter("type")).andReturn(RecordAttach.ATTACH_TYPE_DOC.toString());
	    EasyMock.expect(mockRequest.getParameter("petName")).andReturn(null);
	    
	    try {
			String filepath = UPLOAD_PATH + "/123.txt";
			FileHelper.writeFile(new File(filepath), "卜贝求真。");
	        
	        mocksControl.replay(); 
			upload.processUploadFile(mockRequest, filepath, "123.txt");
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Assert.assertFalse(e.getMessage(), true);
		}
	    
	    TestUtil.printEntity(super.permissionHelper, "RecordAttach"); 
    }
	
}
