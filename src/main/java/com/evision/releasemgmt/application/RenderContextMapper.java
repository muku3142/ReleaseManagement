package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.template.view.CommentView;
import com.evision.releasemgmt.domain.template.view.CommitView;
import com.evision.releasemgmt.domain.template.view.GroupView;
import com.evision.releasemgmt.domain.template.view.ItemView;
import com.evision.releasemgmt.domain.template.view.ProductView;
import com.evision.releasemgmt.domain.template.view.PullRequestView;
import com.evision.releasemgmt.domain.template.view.ReleaseView;

import java.util.LinkedHashMap;
import java.util.Map;

// The engine is handed Maps, not the view records themselves. jmustache resolves a POJO property by
// reflecting for a field or an accessor; giving it a record would put method invocation back on the
// table, which is exactly what TMPL-02 forbids. A Map has one lookup path and no reflection.
public final class RenderContextMapper {

    private RenderContextMapper() {
    }

    public static Map<String, Object> toContext(ReleaseView view) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("release", releaseMap(view));
        root.put("items", view.items().stream().map(RenderContextMapper::itemMap).toList());
        root.put("groups", view.groups().stream().map(RenderContextMapper::groupMap).toList());
        root.put("products", view.products().stream().map(RenderContextMapper::productMap).toList());
        return root;
    }

    private static Map<String, Object> releaseMap(ReleaseView view) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", nullToEmpty(view.name()));
        map.put("version", nullToEmpty(view.version()));
        map.put("status", nullToEmpty(view.status()));
        map.put("jiraProjectKey", nullToEmpty(view.jiraProjectKey()));
        map.put("createdAt", nullToEmpty(view.createdAt()));
        map.put("frozenAt", nullToEmpty(view.frozenAt()));
        map.put("lastRefreshedAt", nullToEmpty(view.lastRefreshedAt()));
        return map;
    }

    private static Map<String, Object> itemMap(ItemView item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", nullToEmpty(item.title()));
        map.put("isManual", item.isManual());
        map.put("key", nullToEmpty(item.key()));
        map.put("issueType", nullToEmpty(item.issueType()));
        map.put("status", nullToEmpty(item.status()));
        map.put("resolution", nullToEmpty(item.resolution()));
        map.put("assignee", nullToEmpty(item.assignee()));
        map.put("reporter", nullToEmpty(item.reporter()));
        map.put("component", nullToEmpty(item.component()));
        map.put("description", nullToEmpty(item.description()));
        map.put("category", nullToEmpty(item.category()));
        map.put("comments", item.comments().stream().map(RenderContextMapper::commentMap).toList());
        map.put("commits", item.commits().stream().map(RenderContextMapper::commitMap).toList());
        map.put("pullRequests", item.pullRequests().stream().map(RenderContextMapper::pullRequestMap).toList());
        return map;
    }

    private static Map<String, Object> groupMap(GroupView group) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("groupName", nullToEmpty(group.groupName()));
        map.put("items", group.items().stream().map(RenderContextMapper::itemMap).toList());
        return map;
    }

    private static Map<String, Object> productMap(ProductView product) {
        return Map.of("name", nullToEmpty(product.name()));
    }

    private static Map<String, Object> commentMap(CommentView comment) {
        return Map.of("text", nullToEmpty(comment.text()));
    }

    private static Map<String, Object> commitMap(CommitView commit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", nullToEmpty(commit.id()));
        map.put("message", nullToEmpty(commit.message()));
        map.put("author", nullToEmpty(commit.author()));
        map.put("url", nullToEmpty(commit.url()));
        return map;
    }

    private static Map<String, Object> pullRequestMap(PullRequestView pr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", nullToEmpty(pr.id()));
        map.put("title", nullToEmpty(pr.title()));
        map.put("status", nullToEmpty(pr.status()));
        map.put("url", nullToEmpty(pr.url()));
        return map;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
