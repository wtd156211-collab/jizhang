package com.scaffold.bookkeeping.ocr;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;

/** 使用阿里云官方 SDK 完成 AK 签名，直接上传私有文件，不生成公网图片 URL。 */
@Component
public class AliyunOcrTransport {
    private final ObjectMapper json;
    public AliyunOcrTransport(ObjectMapper json) { this.json = json; }
    public JsonNode recognize(Path original, String accessKeyId, String accessKeySecret, String documentType) throws Exception {
        Config config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret)
                .setEndpoint("ocr-api.cn-hangzhou.aliyuncs.com").setProtocol("HTTPS").setRegionId("cn-hangzhou");
        Client client = new Client(config);
        RuntimeOptions runtime = new RuntimeOptions().setConnectTimeout(5000).setReadTimeout(15000).setAutoretry(false);
        try (var input = Files.newInputStream(original)) {
            var response = client.recognizeAllTextWithOptions(new RecognizeAllTextRequest().setType(documentType).setBody(input), runtime);
            if (response == null || response.getBody() == null) throw new IllegalStateException("Empty OCR response");
            return json.valueToTree(response.getBody().toMap());
        }
    }
}
