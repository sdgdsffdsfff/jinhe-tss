package com.jinhe.tss.cms;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import com.jinhe.tss.cms.action.ArticleAction;
import com.jinhe.tss.cms.action.ChannelAction;
import com.jinhe.tss.cms.entity.Article;
import com.jinhe.tss.cms.entity.Channel;
import com.jinhe.tss.cms.publish.PublishManger;
import com.jinhe.tss.cms.service.IArticleService;
import com.jinhe.tss.framework.Config;
import com.jinhe.tss.framework.test.TestUtil;
import com.jinhe.tss.util.BeanUtil;
import com.jinhe.tss.util.FileHelper;

public class AbstractTestSupport extends TxSupportTest4CMS {
    
    @Autowired protected ChannelAction channelAction;
    @Autowired protected ArticleAction articleAction;
    
    @Autowired protected IArticleService articleService;
    @Autowired protected PublishManger   publishManger;
    
    // 新建站点
    protected Channel createSite() {
        Channel site = new Channel();
        site.setParentId(CMSConstants.HEAD_NODE_ID);
        site.setName("我的门户" + System.currentTimeMillis());
        site.setPath(super.tempDir1.getPath());
        site.setDocPath("doc");
        site.setImagePath("img");
        site = channelService.createChannel(site);
        Long siteId = site.getId();
        assertNotNull(siteId);
        
        return site;
    }
    
    // 新建栏目
    protected Channel createChannel(String name, Channel site, Long parentId) {
        Channel channel = new Channel();
        BeanUtil.copy(channel, site, new String[]{"id", "name"});
        channel.setName(name);
        channel.setParentId(parentId);
        channel = channelService.createChannel(channel);
        
        return channel;
    }
    
    protected Article createArticle(Channel channel, Long tempArticleId) {
        Article article = new Article();
        article.setTitle("历史的轮回怪圈");
        article.setAuthor("Jon.King");
        article.setSubtitle("对于当前中国社会矛盾的历史思考");
        article.setKeyword("历史 轮回 社会矛盾");
        article.setSummary("历史 轮回 社会矛盾");
        article.setOverdueDate(new Date(System.currentTimeMillis() + 1000*60*60*24*365));
        article.setChannel(channel);
        
        String content = " 最近几年以来，社会矛盾逐渐成为了中国公众瞩目的焦点---" +
                "官员腐败，贫富差距扩大化，三农问题，仇富心态，教育改革，房产价格，医疗社保等一系列问题犹如走马灯" +
                "一般纷纷闯入人们的视野，同时，许多富于代表性的事件也先后挑战着中国人眼球和心理的承受能力。" +
                "我们的社会怎么了？相信只要稍微有点头脑的人都会问这个问题，也有很多人提出了解答。其中，有一种最为" +
                "通行的说法就是:历史的轮回。具体地说来，历朝历代的统治时期大致可以分为三个阶段：" +
                "1。天下创世 2。矛盾丛生3。总崩溃    个别王朝由于在中晚期实行了一些改革，缓和了社会矛盾，" +
                "从而延长了统治时间。这是中国历史所特有的周期性振荡。综观国史，史实让笔者也不得不承认上述说法的合理性。" +
                "那么，目前的中国是否在重新陷入这个历史的轮回怪圈呢？这倒的确是一个值得商榷的问题。";
        
		request.addParameter("articleContent", content);
		request.addParameter("articleId", tempArticleId.toString());
        
		request.addParameter("attachList", "1,2,3,4");
		request.addParameter("isCommit", "false");
		articleAction.saveArticleInfo(response, request, article);
        Long articleId = article.getId();
        assertNotNull(articleId);
        
        return article;
    }
    
    // 上传附件
    protected void uploadDocFile(Long channelId, Long articleId) {
    	UploadServlet upload = new UploadServlet();
    	
	    IMocksControl mocksControl =  EasyMock.createControl();
	    HttpServletRequest mockRequest = mocksControl.createMock(HttpServletRequest.class);
	    
	    EasyMock.expect(mockRequest.getParameter("articleId")).andReturn(articleId.toString());
	    EasyMock.expect(mockRequest.getParameter("channelId")).andReturn(channelId.toString());
	    EasyMock.expect(mockRequest.getParameter("type")).andReturn(CMSConstants.ATTACHMENTTYPE_OFFICE.toString());
	    EasyMock.expect(mockRequest.getParameter("petName")).andReturn(null);
	    
	    try {
	    	Part part = mocksControl.createMock(Part.class);
	    	EasyMock.expect(mockRequest.getPart("file")).andReturn(part).anyTimes();
	    	
	    	// 上传文本文件
			EasyMock.expect(part.getHeader("content-disposition")).andReturn("filename=\"123.txt\"");
			
			// 代替part.write()
			FileHelper.writeFile(new File(Config.UPLOAD_PATH + "/123.txt"), "我们爱技术创新。");
	        part.write("123.txt");
	        EasyMock.expectLastCall(); // void 类型方法的mock
	        
	        mocksControl.replay(); 
			upload.doPost(mockRequest, response);
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Assert.assertFalse(e.getMessage(), true);
		}
	    
	    TestUtil.printEntity(super.permissionHelper, "Attachment"); 
    }
    
    // 上传图片
    protected void uploadImgFile(Long channelId, Long articleId) {
    	UploadServlet upload = new UploadServlet();
    	
	    IMocksControl mocksControl =  EasyMock.createControl();
	    HttpServletRequest mockRequest = mocksControl.createMock(HttpServletRequest.class);
	    
	    EasyMock.expect(mockRequest.getParameter("articleId")).andReturn(articleId.toString());
	    EasyMock.expect(mockRequest.getParameter("channelId")).andReturn(channelId.toString());
	    EasyMock.expect(mockRequest.getParameter("type")).andReturn(CMSConstants.ATTACHMENTTYPE_OFFICE.toString());
	    EasyMock.expect(mockRequest.getParameter("petName")).andReturn(null);
	    
	    try {
	    	Part part = mocksControl.createMock(Part.class);
	    	EasyMock.expect(mockRequest.getPart("file")).andReturn(part).anyTimes();
	    	
			// 先用java创建一张图片
			int width = 100, height = 100;   
	        String s = "TSS LOGO";   
	        
	        String filePath = Config.UPLOAD_PATH + "/111.jpg";
	        File file = new File(filePath);   
	           
	        Font font = new Font("Serif", Font.BOLD, 10);   
	        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);   
	        Graphics2D g2 = (Graphics2D)bi.getGraphics();   
	        g2.setBackground(Color.WHITE);   
	        g2.clearRect(0, 0, width, height);   
	        g2.setPaint(Color.RED);   
	           
	        FontRenderContext context = g2.getFontRenderContext();   
	        Rectangle2D bounds = font.getStringBounds(s, context);   
	        double x = (width - bounds.getWidth()) / 2;   
	        double y = (height - bounds.getHeight()) / 2;   
	        double ascent = -bounds.getY();   
	        double baseY = y + ascent;   
	           
	        g2.drawString(s, (int)x, (int)baseY);   
	        ImageIO.write(bi, "jpg", file); 
	        
	        EasyMock.expect(part.getHeader("content-disposition")).andReturn("filename=\"111.jpg\"");
			
			// 代替part.write()
	        part.write("111.jpg");
	        EasyMock.expectLastCall();
	        
	        mocksControl.replay(); 
			upload.doPost(mockRequest, response);
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Assert.assertFalse(e.getMessage(), true);
		}
	    
	    TestUtil.printEntity(super.permissionHelper, "Attachment"); 
    }
    
    protected void deleteSite(Long siteId) {
        channelService.deleteChannel(siteId);
        List<?> list = channelService.getAllSiteChannelList();
        assertNotNull(list);
        assertTrue(list.isEmpty());
        
        String hql = "from Article a where a.channel.site.id=? ";
        List<?> articles = channelDao.getEntities(hql, siteId);
        assertTrue(articles.isEmpty());
    }
    
    protected List<?> getArticlesByChannel(Long channelId) {
        String hql = "from Article a where a.channel.id = ? ";
        return channelDao.getEntities(hql, channelId);
    }

}
