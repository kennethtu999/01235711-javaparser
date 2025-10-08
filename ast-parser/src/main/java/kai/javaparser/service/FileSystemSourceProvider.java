package kai.javaparser.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import kai.javaparser.ast.model.FileAstData;
import kai.javaparser.diagram.idx.AstIndex;

/**
 * 檔案系統原始碼提供者
 * 從檔案系統中讀取Java原始碼檔案
 */
@Component
public class FileSystemSourceProvider implements SourceProvider {
    private static final Logger logger = LoggerFactory.getLogger(FileSystemSourceProvider.class);

    private final AstIndex astIndex;

    @Autowired
    public FileSystemSourceProvider(AstIndex astIndex) {
        this.astIndex = astIndex;
    }

    @Override
    public String getSourceCode(String classFqn) {
        logger.debug("獲取類別原始碼: {}", classFqn);

        try {
            // 從AST索引中獲取檔案資訊
            FileAstData fileData = astIndex.getAstDataByClassFqn(classFqn);
            if (fileData == null) {
                logger.warn("找不到類別: {}", classFqn);
                return null;
            }

            // 讀取原始碼檔案
            Path sourcePath = Paths.get(fileData.getAbsolutePath());
            if (!Files.exists(sourcePath)) {
                logger.warn("原始碼檔案不存在: {}", sourcePath);
                return null;
            }

            String sourceCode = Files.readString(sourcePath);
            logger.debug("成功讀取原始碼，長度: {} 字元", sourceCode.length());
            return sourceCode;

        } catch (IOException e) {
            logger.error("讀取原始碼檔案失敗: {}", classFqn, e);
            return null;
        }
    }

    @Override
    public Map<String, String> getSourceCodes(Set<String> classFqns) {
        logger.debug("批量獲取類別原始碼，數量: {}", classFqns.size());

        Map<String, String> sourceCodes = new HashMap<>();
        for (String classFqn : classFqns) {
            String sourceCode = getSourceCode(classFqn);
            if (sourceCode != null) {
                sourceCodes.put(classFqn, sourceCode);
            }
        }

        logger.debug("成功獲取 {} 個類別的原始碼", sourceCodes.size());
        return sourceCodes;
    }

    @Override
    public boolean exists(String classFqn) {
        FileAstData fileData = astIndex.getAstDataByClassFqn(classFqn);
        if (fileData == null) {
            return false;
        }

        Path sourcePath = Paths.get(fileData.getAbsolutePath());
        return Files.exists(sourcePath);
    }

    @Override
    public String getProviderName() {
        return "FileSystem";
    }
}
