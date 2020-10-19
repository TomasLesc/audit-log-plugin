/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package io.jenkins.plugins.audit.plugins;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.util.Transform;
import org.apache.logging.log4j.util.Strings;

import io.jenkins.plugins.audit.helpers.DateTimeHelper;

/**
 * Outputs events as rows in an HTML table on an HTML page.
 * <p>
 * Appenders using this layout should have their encoding set to UTF-8 or UTF-16, otherwise events containing non ASCII
 * characters could result in corrupted log files.
 * </p>
 */
@Plugin(name = "CustomHTMLLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public final class CustomHTMLLayout extends AbstractStringLayout {

    /**
     * Default font family: {@value}.
     */
    public static final String DEFAULT_FONT_FAMILY = "arial,sans-serif";

    private static final String TRACE_PREFIX = "<br />&nbsp;&nbsp;&nbsp;&nbsp;";
    private static final String REGEXP = Strings.LINE_SEPARATOR.equals("\n") ? "\n" : Strings.LINE_SEPARATOR + "|\n";
    private static final String DEFAULT_TITLE = "Log4j Log Messages";
    private static final String DEFAULT_CONTENT_TYPE = "text/html";

    // Print no location info by default
    private final boolean locationInfo;
    private final String title;
    private final String contentType;
    private final String font;
    private final String fontSize;
    private final String headerSize;

    /**Possible font sizes */
    public static enum FontSize {
        SMALLER("smaller"), XXSMALL("xx-small"), XSMALL("x-small"), SMALL("small"), MEDIUM("medium"), LARGE("large"),
        XLARGE("x-large"), XXLARGE("xx-large"),  LARGER("larger");

        private final String size;

        private FontSize(final String size) {
            this.size = size;
        }

        public String getFontSize() {
            return size;
        }

        public static FontSize getFontSize(final String size) {
            for (final FontSize fontSize : values()) {
                if (fontSize.size.equals(size)) {
                    return fontSize;
                }
            }
            return SMALL;
        }

        public FontSize larger() {
            return this.ordinal() < XXLARGE.ordinal() ? FontSize.values()[this.ordinal() + 1] : this;
        }
    }

    private CustomHTMLLayout(final boolean locationInfo, final String title, final String contentType, final Charset charset,
            final String font, final String fontSize, final String headerSize) {
        super(charset);
        this.locationInfo = locationInfo;
        this.title = title;
        this.contentType = addCharsetToContentType(contentType);
        this.font = font;
        this.fontSize = fontSize;
        this.headerSize = headerSize;
    }

    /**
     * For testing purposes.
     */
    public String getTitle() {
        return title;
    }

    /**
     * For testing purposes.
     */
    public boolean isLocationInfo() {
        return locationInfo;
    }

    @Override
    public boolean requiresLocation() {
        return locationInfo;
    }

    private String addCharsetToContentType(final String contentType) {
        if (contentType == null) {
            return DEFAULT_CONTENT_TYPE + "; charset=" + getCharset();
        }
        return contentType.contains("charset") ? contentType : contentType + "; charset=" + getCharset();
    }

    /**
     * Formats as a String.
     *
     * @param event The Logging Event.
     * @return A String containing the LogEvent as HTML.
     */
    @Override
    public String toSerializable(final LogEvent event) {
        final StringBuilder sbuf = getStringBuilder();

        sbuf.append(Strings.LINE_SEPARATOR).append("<tr>").append(Strings.LINE_SEPARATOR);

        sbuf.append("<td>");   
	sbuf.append(DateTimeHelper.formatDateISO(event.getTimeMillis()));
        sbuf.append("</td>").append(Strings.LINE_SEPARATOR);

        final String escapedThread = Transform.escapeHtmlTags(event.getThreadName());
        sbuf.append("<td title=\"").append(escapedThread).append(" thread\">");
        sbuf.append(escapedThread);
        sbuf.append("</td>").append(Strings.LINE_SEPARATOR);

        sbuf.append("<td title=\"Level\"");
        if (event.getLevel().equals(Level.DEBUG)) {
            sbuf.append(" class=\"debug\">");
            sbuf.append(Transform.escapeHtmlTags(String.valueOf(event.getLevel())));
        } else if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
            sbuf.append(" class=\"warn\">");
            sbuf.append(Transform.escapeHtmlTags(String.valueOf(event.getLevel())));
        } else {
            sbuf.append(Transform.escapeHtmlTags(String.valueOf(event.getLevel())));
        }
        sbuf.append("</td>").append(Strings.LINE_SEPARATOR);

        String escapedLogger = Transform.escapeHtmlTags(event.getLoggerName());
        if (Strings.isEmpty(escapedLogger)) {
            escapedLogger = LoggerConfig.ROOT;
        }
        sbuf.append("<td title=\"").append(escapedLogger).append(" logger\">");
        sbuf.append(escapedLogger);
        sbuf.append("</td>").append(Strings.LINE_SEPARATOR);

        if (locationInfo) {
            final StackTraceElement element = event.getSource();
            sbuf.append("<td>");
            sbuf.append(Transform.escapeHtmlTags(element.getFileName()));
            sbuf.append(':');
            sbuf.append(element.getLineNumber());
            sbuf.append("</td>").append(Strings.LINE_SEPARATOR);
        }

        sbuf.append("<td title=\"Message\">");
        sbuf.append(Transform.escapeHtmlTags(event.getMessage().getFormattedMessage()).replaceAll(REGEXP, "<br />"));
        sbuf.append("</td>").append(Strings.LINE_SEPARATOR);
        sbuf.append("</tr>").append(Strings.LINE_SEPARATOR);

        if (event.getContextStack() != null && !event.getContextStack().isEmpty()) {
            sbuf.append("<tr><td class=\"context\" ");
            sbuf.append("colspan=\"6\" ");
            sbuf.append("title=\"Nested Diagnostic Context\">");
            sbuf.append("NDC: ").append(Transform.escapeHtmlTags(event.getContextStack().toString()));
            sbuf.append("</td></tr>").append(Strings.LINE_SEPARATOR);
        }

        if (event.getContextData() != null && !event.getContextData().isEmpty()) {
            sbuf.append("<tr><td class=\"context\" ");
            sbuf.append("colspan=\"6\" ");
            sbuf.append("title=\"Mapped Diagnostic Context\">");
            sbuf.append("MDC: ").append(Transform.escapeHtmlTags(event.getContextData().toMap().toString()));
            sbuf.append("</td></tr>").append(Strings.LINE_SEPARATOR);
        }

        final Throwable throwable = event.getThrown();
        if (throwable != null) {
            sbuf.append("<tr><td class=\"throw\" ");
            sbuf.append("colspan=\"6\">");
            appendThrowableAsHtml(throwable, sbuf);
            sbuf.append("</td></tr>").append(Strings.LINE_SEPARATOR);
        }

        return sbuf.toString();
    }

    @Override
    /**
     * @return The content type.
     */
    public String getContentType() {
        return contentType;
    }

    private void appendThrowableAsHtml(final Throwable throwable, final StringBuilder sbuf) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        try {
            throwable.printStackTrace(pw);
        } catch (final RuntimeException ex) {
            // Ignore the exception.
        }
        pw.flush();
        final LineNumberReader reader = new LineNumberReader(new StringReader(sw.toString()));
        final ArrayList<String> lines = new ArrayList<>();
        try {
          String line = reader.readLine();
          while (line != null) {
            lines.add(line);
            line = reader.readLine();
          }
        } catch (final IOException ex) {
            if (ex instanceof InterruptedIOException) {
                Thread.currentThread().interrupt();
            }
            lines.add(ex.toString());
        }
        boolean first = true;
        for (final String line : lines) {
            if (!first) {
                sbuf.append(TRACE_PREFIX);
            } else {
                first = false;
            }
            sbuf.append(Transform.escapeHtmlTags(line));
            sbuf.append(Strings.LINE_SEPARATOR);
        }
    }

    private StringBuilder appendLs(final StringBuilder sbuilder, final String s) {
        sbuilder.append(s).append(Strings.LINE_SEPARATOR);
        return sbuilder;
    }

    private StringBuilder append(final StringBuilder sbuilder, final String s) {
        sbuilder.append(s);
        return sbuilder;
    }

    /**
     * Returns appropriate HTML headers.
     * @return The header as a byte array.
     */
    @Override
    public byte[] getHeader() {
        final StringBuilder sbuf = new StringBuilder();
        append(sbuf, "<!DOCTYPE html>");
        appendLs(sbuf, "<html>");
        appendLs(sbuf, "<head>");
        append(sbuf, "<meta charset=\"");
        append(sbuf, getCharset().toString());
        appendLs(sbuf, "\"/>");
        append(sbuf, "<title>").append(title);
        appendLs(sbuf, "</title>");
	appendLs(sbuf, "<style type=\"text/css\">");
        appendLs(sbuf, "<!--");
        append(sbuf, "body, table {font-family:").append(font).append("; font-size: ").append(headerSize);
	appendLs(sbuf, ";}");
        appendLs(sbuf, "th {background: #f0f0f0; font-weight: bold; text-align: left;}");
	appendLs(sbuf, "tr:nth-child(odd) {background-color: #fbfbfb;}");
	appendLs(sbuf, "tr:hover {background-color: #e8e8e8 !important;}");
	appendLs(sbuf, "td.warn {color: #993300; font-weight: bold;}");
	appendLs(sbuf, "td.debug {color: #339933;}");
	append(sbuf, "td.context {background-color: #fbfbfb; font-size: ").append(fontSize);
	appendLs(sbuf, ";}");
	append(sbuf, "td.throw {background-color: #993300; color: White; font-size: ").append(fontSize);
	appendLs(sbuf, ";}");
        appendLs(sbuf, "-->");
        appendLs(sbuf, "</style>");        
	appendLs(sbuf, "</head>");
        appendLs(sbuf, "<body bgcolor=\"#FFFFFF\" topmargin=\"6\" leftmargin=\"6\">");
        appendLs(sbuf, "<hr size=\"1\" noshade=\"noshade\">");
        appendLs(sbuf, "Log session start time " + DateTimeHelper.formatDateISO(System.currentTimeMillis()) + "<br>");
        appendLs(sbuf, "<br>");
        appendLs(sbuf,
                "<table cellspacing=\"0\" cellpadding=\"4\" border=\"1\" bordercolor=\"#224466\" width=\"100%\">");
        appendLs(sbuf, "<tr>");
        appendLs(sbuf, "<th>Time</th>");
        appendLs(sbuf, "<th>Thread</th>");
        appendLs(sbuf, "<th>Level</th>");
        appendLs(sbuf, "<th>Logger</th>");
        if (locationInfo) {
            appendLs(sbuf, "<th>File:Line</th>");
        }
        appendLs(sbuf, "<th>Message</th>");
        appendLs(sbuf, "</tr>");
        return sbuf.toString().getBytes(getCharset());
    }

    /**
     * Returns the appropriate HTML footers.
     * @return the footer as a byte array.
     */
    @Override
    public byte[] getFooter() {
        final StringBuilder sbuf = new StringBuilder();
        appendLs(sbuf, "</table>");
        appendLs(sbuf, "<br>");
        appendLs(sbuf, "</body></html>");
        return getBytes(sbuf.toString());
    }

    /**
     * Creates an HTML Layout using the default settings.
     *
     * @return an HTML Layout.
     */
    public static CustomHTMLLayout createDefaultLayout() {
        return newBuilder().build();
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<CustomHTMLLayout> {

        @PluginBuilderAttribute
        private boolean locationInfo = false;

        @PluginBuilderAttribute
        private String title = DEFAULT_TITLE;

        @PluginBuilderAttribute
        private String contentType = null; // defer default value in order to use specified charset

        @PluginBuilderAttribute
        private Charset charset = StandardCharsets.UTF_8;

        @PluginBuilderAttribute
        private FontSize fontSize = FontSize.XSMALL;

        @PluginBuilderAttribute
        private String fontName = DEFAULT_FONT_FAMILY;

        private Builder() {
        }

        public Builder withLocationInfo(final boolean locationInfo) {
            this.locationInfo = locationInfo;
            return this;
        }

        public Builder withTitle(final String title) {
            this.title = title;
            return this;
        }

        public Builder withContentType(final String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder withCharset(final Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder withFontSize(final FontSize fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public Builder withFontName(final String fontName) {
            this.fontName = fontName;
            return this;
        }

        @Override
        public CustomHTMLLayout build() {
            // TODO: extract charset from content-type
            if (contentType == null) {
                contentType = DEFAULT_CONTENT_TYPE + "; charset=" + charset;
            }
            return new CustomHTMLLayout(locationInfo, title, contentType, charset, fontName, fontSize.getFontSize(),
                fontSize.larger().getFontSize());
        }
    }
}
