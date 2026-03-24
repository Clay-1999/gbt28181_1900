package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GB/T 28181 XML 序列化/反序列化工具。
 * 使用 JAXB 注解驱动，JAXBContext 按类型缓存。
 */
public final class GbXmlMapper {

    private static final ConcurrentHashMap<Class<?>, JAXBContext> CONTEXT_CACHE = new ConcurrentHashMap<>();

    private GbXmlMapper() {}

    /** 将 JAXB 对象序列化为 XML 字符串（GB2312 编码声明）。 */
    public static String toXml(Object obj) {
        try {
            JAXBContext ctx = contextFor(obj.getClass());
            Marshaller m = ctx.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);
            m.setProperty(Marshaller.JAXB_ENCODING, "GB2312");
            // FRAGMENT=true 让 JAXB 不输出 XML 声明，再手动拼接，避免 standalone="yes"
            m.setProperty(Marshaller.JAXB_FRAGMENT, true);
            StringWriter sw = new StringWriter();
            sw.write("<?xml version=\"1.0\" encoding=\"GB2312\"?>");
            m.marshal(obj, sw);
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("JAXB marshal 失败: " + obj.getClass().getSimpleName(), e);
        }
    }

    /** 将 XML 字符串反序列化为指定类型的 JAXB 对象。 */
    @SuppressWarnings("unchecked")
    public static <T> T fromXml(String xml, Class<T> clazz) {
        try {
            // GB2312 编码的 XML 需要先转为 UTF-8 字节再解析，避免编码声明与实际字节不符
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            String normalized = new String(bytes, StandardCharsets.UTF_8)
                    .replaceFirst("encoding=\"GB2312\"", "encoding=\"UTF-8\"")
                    .replaceFirst("encoding='GB2312'", "encoding='UTF-8'");
            JAXBContext ctx = contextFor(clazz);
            Unmarshaller u = ctx.createUnmarshaller();
            return (T) u.unmarshal(new StringReader(normalized));
        } catch (Exception e) {
            throw new IllegalStateException("JAXB unmarshal 失败: " + clazz.getSimpleName(), e);
        }
    }

    private static JAXBContext contextFor(Class<?> clazz) {
        return CONTEXT_CACHE.computeIfAbsent(clazz, c -> {
            try {
                return JAXBContext.newInstance(c);
            } catch (Exception e) {
                throw new IllegalStateException("JAXBContext 创建失败: " + c.getSimpleName(), e);
            }
        });
    }
}
