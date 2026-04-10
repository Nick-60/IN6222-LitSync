package com.in6222.litsync.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.in6222.litsync.R;
import com.in6222.litsync.databinding.FragmentSearchBinding;
import com.in6222.litsync.databinding.FragmentTrendingBinding;
import com.in6222.litsync.model.ArxivAuthor;
import com.in6222.litsync.model.ArxivEntry;
import com.in6222.litsync.model.ArxivLink;
import com.in6222.litsync.model.PaperItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment 基类，抽取公共方法。
 */
public abstract class BaseFragment extends Fragment {

    /**
     * 显示加载中状态。
     */
    protected void showLoading(FragmentTrendingBinding binding) {
        if (binding == null) return;
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.VISIBLE);
    }

    /**
     * 显示加载中状态（SearchFragment 版本）。
     */
    protected void showLoading(FragmentSearchBinding binding) {
        if (binding == null) return;
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.VISIBLE);
    }

    /**
     * 显示空数据状态。
     */
    protected void showEmpty(FragmentTrendingBinding binding, String message) {
        if (binding == null) return;
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.VISIBLE);
        binding.statusText.setText(message);
    }

    /**
     * 显示空数据状态（SearchFragment 版本）。
     */
    protected void showEmpty(FragmentSearchBinding binding, String message) {
        if (binding == null) return;
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.VISIBLE);
        binding.statusText.setText(message);
    }

    /**
     * 显示错误状态。
     */
    protected void showError(FragmentTrendingBinding binding, String message) {
        if (binding == null) return;
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.VISIBLE);
        binding.statusText.setText(message);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    /**
     * 显示错误状态（SearchFragment 版本）。
     */
    protected void showError(FragmentSearchBinding binding, String message) {
        if (binding == null) return;
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.VISIBLE);
        binding.statusText.setText(message);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    /**
     * 显示内容。
     */
    protected void showContent(FragmentTrendingBinding binding) {
        if (binding == null) return;
        binding.statusCard.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    /**
     * 显示内容（SearchFragment 版本）。
     */
    protected void showContent(FragmentSearchBinding binding) {
        if (binding == null) return;
        binding.statusCard.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
        binding.statusText.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    /**
     * 将 ArxivEntry 列表转换为 PaperItem 列表。
     */
    protected List<PaperItem> mapEntries(@Nullable List<ArxivEntry> entries) {
        List<PaperItem> items = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return items;
        }
        for (ArxivEntry entry : entries) {
            String title = entry.getTitle();
            String summary = entry.getSummary();
            String published = entry.getPublished();
            String author = formatAuthors(entry.getAuthors());
            String link = extractLink(entry.getLinks());
            items.add(new PaperItem(0, title, author, summary, published, link));
        }
        return items;
    }

    /**
     * 格式化作者列表。
     */
    protected String formatAuthors(@Nullable List<ArxivAuthor> authors) {
        if (authors == null || authors.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < authors.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(authors.get(i).getName());
        }
        return builder.toString();
    }

    /**
     * 提取论文链接。
     */
    protected String extractLink(@Nullable List<ArxivLink> links) {
        if (links == null || links.isEmpty()) {
            return "";
        }
        for (ArxivLink link : links) {
            if (link.getHref() == null) {
                continue;
            }
            if ("alternate".equals(link.getRel()) || "text/html".equals(link.getType())) {
                return link.getHref();
            }
        }
        return links.get(0).getHref();
    }
}
