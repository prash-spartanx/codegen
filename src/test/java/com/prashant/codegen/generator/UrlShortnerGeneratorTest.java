package com.prashant.codegen.generator;

import com.prashant.codegen.model.KafkaSpec;
import com.prashant.codegen.model.KafkaTopicSpec;
import com.prashant.codegen.model.UrlShortenerSpec;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UrlShortnerGeneratorTest {
        private UrlShortnerGenerator generator ;

    private Configuration cfg;
    @BeforeEach
    void setUp() throws Exception {
        cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setClassForTemplateLoading(this.getClass(), "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        generator = new UrlShortnerGenerator();
    }
    @Test
    void testGenerateKafkaProducer() throws Exception {
        UrlShortenerSpec spec1 = new UrlShortenerSpec("http://localhost:8080", 6, 30, true);
        UrlShortenerSpec spec2 = new UrlShortenerSpec("http://localhost:8080", 8, 60, false);
        String result1 = generator.generate(spec1, cfg);
        System.out.println(result1);
        String result2 = generator.generate(spec2, cfg);
        System.out.println(result2);
    }

}
