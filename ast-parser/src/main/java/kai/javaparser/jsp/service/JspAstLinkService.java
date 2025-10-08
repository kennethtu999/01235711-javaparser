package kai.javaparser.jsp.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kai.javaparser.ast.entity.Neo4jMethodNode;
import kai.javaparser.ast.repository.Neo4jMethodNodeRepository;
import kai.javaparser.jsp.entity.JSPBackendMethodNode;
import kai.javaparser.jsp.repository.JSPBackendMethodRepository;

/**
 * 簡單的 JSP-AST 連結服務
 * 只做基本的精確匹配連結
 */
@Service
@Transactional
public class JspAstLinkService {

    private static final Logger logger = LoggerFactory.getLogger(JspAstLinkService.class);

    @Autowired
    private JSPBackendMethodRepository jspBackendMethodRepository;

    @Autowired
    private Neo4jMethodNodeRepository neo4jMethodNodeRepository;

    /**
     * 為所有 JSP 後端方法建立與 AST 方法的連結
     * 只做精確匹配：類別名稱和方法名稱完全匹配
     */
    public int linkAllJspBackendMethods() {
        logger.info("開始為所有 JSP 後端方法建立 AST 連結");

        List<JSPBackendMethodNode> jspMethods = jspBackendMethodRepository.findAll();
        int linkedCount = 0;

        for (JSPBackendMethodNode jspMethod : jspMethods) {
            if (linkJspMethodToAst(jspMethod)) {
                linkedCount++;
            }
        }

        logger.info("JSP-AST 連結完成: {} / {} 個方法成功連結", linkedCount, jspMethods.size());
        return linkedCount;
    }

    /**
     * 為特定 JSP 後端方法建立與 AST 方法的連結
     * 只做精確匹配
     */
    public boolean linkJspMethodToAst(JSPBackendMethodNode jspMethod) {
        if (jspMethod == null || jspMethod.getClassName() == null || jspMethod.getMethodName() == null) {
            return false;
        }

        try {
            // 精確匹配：類別名稱和方法名稱完全匹配
            Optional<Neo4jMethodNode> astMethod = neo4jMethodNodeRepository.findByNameAndClassName(
                    jspMethod.getMethodName(),
                    jspMethod.getClassName());

            if (astMethod.isPresent()) {
                jspMethod.linkToAstMethod(astMethod.get().getId());
                jspBackendMethodRepository.save(jspMethod);
                logger.debug("成功連結: {} -> {}", jspMethod.getId(), astMethod.get().getId());
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("連結 JSP 方法時發生錯誤: {}.{} - {}",
                    jspMethod.getClassName(), jspMethod.getMethodName(), e.getMessage());
            return false;
        }
    }

    /**
     * 移除特定 JSP 方法的連結
     */
    public void unlinkJspMethod(String jspMethodId) {
        Optional<JSPBackendMethodNode> jspMethod = jspBackendMethodRepository.findById(jspMethodId);
        if (jspMethod.isPresent()) {
            jspMethod.get().unlinkFromAstMethod();
            jspBackendMethodRepository.save(jspMethod.get());
            logger.debug("已移除 JSP 方法的連結: {}", jspMethodId);
        }
    }

    /**
     * 查找已連結到 AST 的 JSP 後端方法
     */
    public List<JSPBackendMethodNode> findLinkedJspMethods() {
        return jspBackendMethodRepository.findAll().stream()
                .filter(JSPBackendMethodNode::isLinkedToAst)
                .toList();
    }

    /**
     * 查找未連結到 AST 的 JSP 後端方法
     */
    public List<JSPBackendMethodNode> findUnlinkedJspMethods() {
        return jspBackendMethodRepository.findAll().stream()
                .filter(method -> !method.isLinkedToAst())
                .toList();
    }
}
