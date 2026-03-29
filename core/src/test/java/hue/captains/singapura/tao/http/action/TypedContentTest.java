package hue.captains.singapura.tao.http.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypedContentTest {

    record TestJs(String body) implements TypedContent.Js {}
    record TestHtml(String body) implements TypedContent.Html {}
    record TestCss(String body) implements TypedContent.Css {}
    record TestSvg(String body) implements TypedContent.Svg {}

    @Test
    void js_hasCorrectContentType() {
        var content = new TestJs("console.log('hello');");
        assertInstanceOf(TypedContent.class, content);
        assertEquals("application/javascript", content.contentType());
        assertEquals("console.log('hello');", content.body());
    }

    @Test
    void html_hasCorrectContentType() {
        var content = new TestHtml("<h1>Hello</h1>");
        assertInstanceOf(TypedContent.class, content);
        assertEquals("text/html", content.contentType());
    }

    @Test
    void css_hasCorrectContentType() {
        var content = new TestCss("body { color: red; }");
        assertInstanceOf(TypedContent.class, content);
        assertEquals("text/css", content.contentType());
    }

    @Test
    void svg_hasCorrectContentType() {
        var content = new TestSvg("<svg></svg>");
        assertInstanceOf(TypedContent.class, content);
        assertEquals("image/svg+xml", content.contentType());
    }

    @Test
    void instanceofCheck_worksForAllSubtypes() {
        // Verify the handler pattern: a single instanceof TypedContent check
        Object js = new TestJs("x");
        Object html = new TestHtml("x");
        Object plainObject = "not typed content";

        assertTrue(js instanceof TypedContent);
        assertTrue(html instanceof TypedContent);
        assertFalse(plainObject instanceof TypedContent);

        // Verify body() and contentType() are accessible via the base interface
        TypedContent tc = (TypedContent) js;
        assertEquals("application/javascript", tc.contentType());
        assertEquals("x", tc.body());
    }
}
