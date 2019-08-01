package com.ztbsuper.dingding;

import com.alibaba.fastjson.JSONObject;
import hudson.ProxyConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import ren.wizard.dingtalkclient.DingTalkClient;
import ren.wizard.dingtalkclient.message.DingMessage;
import ren.wizard.dingtalkclient.message.LinkMessage;
import ren.wizard.dingtalkclient.message.MarkdownMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

/**
 * Created by Marvin on 16/10/8.
 */
public class DingdingServiceImpl implements DingdingService {

    private Logger logger = LoggerFactory.getLogger(DingdingService.class);

    private String jenkinsURL;

    private boolean onStart;

    private boolean onSuccess;

    private boolean onFailed;

    private boolean onAbort;

    private TaskListener listener;

    private AbstractBuild build;

    private static final String apiUrl = "https://oapi.dingtalk.com/robot/send?access_token=";

    private String api;

    private String notifyPeople;

    private String message;

    private String imageUrl;

    private String jumpUrl;

    private String accessToken;

    public DingdingServiceImpl(String jenkinsURL, String token, boolean onStart, boolean onSuccess, boolean onFailed, boolean onAbort, TaskListener listener, AbstractBuild build, String notifyPeople, String message, String imageUrl, String jumpUrl, String accessToken2) {
        this.jenkinsURL = jenkinsURL;
        this.onStart = onStart;
        this.onSuccess = onSuccess;
        this.onFailed = onFailed;
        this.onAbort =  onAbort;
        this.listener = listener;
        this.build = build;
        this.api = apiUrl + token;
        this.notifyPeople = notifyPeople;
        this.message = message;
        this.imageUrl = imageUrl;
        this.jumpUrl = jumpUrl;
        this.accessToken = accessToken2;
    }

    @Override
    public void start() {
        String pic = "http://icon-park.com/imagefiles/loading7_gray.gif";
        String title = String.format("%s%s开始构建", build.getProject().getDisplayName(), build.getDisplayName());
        String content = String.format("项目[%s%s]开始构建", build.getProject().getDisplayName(), build.getDisplayName());

        String link = getBuildUrl();
        if (onStart) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
        }

    }

    private String getBuildUrl() {
        if (jenkinsURL.endsWith("/")) {
            return jenkinsURL + build.getUrl();
        } else {
            return jenkinsURL + "/" + build.getUrl();
        }
    }

    @Override
    public void success() {
        String pic = "http://icons.iconarchive.com/icons/paomedia/small-n-flat/1024/sign-check-icon.png";
        String title = String.format("%s%s构建成功", build.getProject().getDisplayName(), build.getDisplayName());
        String content = String.format("项目[%s%s]构建成功, summary:%s, duration:%s", build.getProject().getDisplayName(), build.getDisplayName(), build.getBuildStatusSummary().message, build.getDurationString());

        String link = getBuildUrl();
        logger.info(link);
        if (onSuccess) {
            logger.info("send link msg from " + listener.toString());
            // sendLinkMessage(link, content, title, pic);
            sendOnSuccessMessage();
        }
    }

    @Override
    public void failed() {
        String pic = "http://www.iconsdb.com/icons/preview/soylent-red/x-mark-3-xxl.png";
        String title = String.format("%s%s构建失败", build.getProject().getDisplayName(), build.getDisplayName());
        String content = String.format("项目[%s%s]构建失败, summary:%s, duration:%s", build.getProject().getDisplayName(), build.getDisplayName(), build.getBuildStatusSummary().message, build.getDurationString());

        String link = getBuildUrl();
        logger.info(link);
        if (onFailed) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
        }
    }

    @Override
    public void abort() {
        String pic = "http://www.iconsdb.com/icons/preview/soylent-red/x-mark-3-xxl.png";
        String title = String.format("%s%s构建中断", build.getProject().getDisplayName(), build.getDisplayName());
        String content = String.format("项目[%s%s]构建中断, summary:%s, duration:%s", build.getProject().getDisplayName(), build.getDisplayName(), build.getBuildStatusSummary().message, build.getDurationString());

        String link = getBuildUrl();
        logger.info(link);
        if (onAbort) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
        }
    }

    private void sendOnSuccessMessage() {
        String buildInfo = String.format("%s%s", build.getProject().getDisplayName(), build.getDisplayName());
        List<String> items = new ArrayList<>();
        String imageURL = MarkdownMessage.getImageText(imageUrl);
        String jumpURL = MarkdownMessage.getLinkText("二维码地址",jumpUrl);
        List<String> atMobiles = Arrays.asList(notifyPeople.split(","));

        items.add(MarkdownMessage.getHeaderText(4,buildInfo));
        items.add(MarkdownMessage.getReferenceText(imageURL));
        items.add("\n");
        items.add(MarkdownMessage.getReferenceText(message));
        for (String item : atMobiles) {
            if (item.length() == 11) {
                items.add(MarkdownMessage.getReferenceText('@'+ item));
            }
        }
        items.add(MarkdownMessage.getReferenceText(jumpURL));

        MarkdownMessage markDownMsg = MarkdownMessage.builder()
        .title(buildInfo)
        .items(items)
        .atMobiles(atMobiles)
        .build();

        System.out.println(markDownMsg.toJson());
        logger.info("markDownMsg = " + markDownMsg.toJson());

        if (!StringUtils.isBlank(message)) {
            sendMessage(markDownMsg);
        }
    }

    private void sendLinkMessage(String link, String msg, String title, String pic) {
        HttpClient client = getHttpClient();
        PostMethod post = new PostMethod(api);

        JSONObject body = new JSONObject();
        body.put("msgtype", "link");


        JSONObject linkObject = new JSONObject();
        linkObject.put("text", msg);
        linkObject.put("title", title);
        linkObject.put("picUrl", pic);
        linkObject.put("messageUrl", link);

        body.put("link", linkObject);
        try {
            post.setRequestEntity(new StringRequestEntity(body.toJSONString(), "application/json", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            logger.error("build request error", e);
        }
        try {
            client.executeMethod(post);
            logger.info(post.getResponseBodyAsString());
        } catch (IOException e) {
            logger.error("send msg error", e);
        }
        post.releaseConnection();
    }

    private void sendMessage(DingMessage message) {
        DingTalkClient dingTalkClient = DingTalkClient.getInstance();
        try {
            dingTalkClient.sendMessage(accessToken, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null && jenkins.proxy != null) {
            ProxyConfiguration proxy = jenkins.proxy;
            if (proxy != null && client.getHostConfiguration() != null) {
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
                String username = proxy.getUserName();
                String password = proxy.getPassword();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
                    logger.info("Using proxy authentication (user=" + username + ")");
                    client.getState().setProxyCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(username, password));
                }
            }
        }
        return client;
    }
}
