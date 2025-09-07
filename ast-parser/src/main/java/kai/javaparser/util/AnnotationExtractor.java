package kai.javaparser.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeLiteral;

import kai.javaparser.model.AnnotationInfo;

/**
 * 註解提取工具類
 * 提供從 Eclipse JDT AST 節點中提取註解信息的功能
 */
public class AnnotationExtractor {

    /**
     * 從註解列表中提取註解信息
     * 
     * @param annotations 註解列表
     * @param cu          編譯單元（用於獲取行號）
     * @return 註解信息列表
     */
    public static List<AnnotationInfo> extractAnnotations(List<?> annotations, CompilationUnit cu) {
        List<AnnotationInfo> annotationInfos = new ArrayList<>();

        if (annotations == null || annotations.isEmpty()) {
            return annotationInfos;
        }

        for (Object annotationObj : annotations) {
            if (annotationObj instanceof Annotation) {
                Annotation annotation = (Annotation) annotationObj;
                AnnotationInfo annotationInfo = extractAnnotationInfo(annotation, cu);
                if (annotationInfo != null) {
                    annotationInfos.add(annotationInfo);
                }
            }
        }

        return annotationInfos;
    }

    /**
     * 從單個註解節點提取註解信息
     * 
     * @param annotation 註解節點
     * @param cu         編譯單元
     * @return 註解信息
     */
    private static AnnotationInfo extractAnnotationInfo(Annotation annotation, CompilationUnit cu) {
        if (annotation == null) {
            return null;
        }

        String annotationName = annotation.getTypeName().getFullyQualifiedName();
        String simpleName = annotation.getTypeName().toString();

        AnnotationInfo annotationInfo = new AnnotationInfo(annotationName, simpleName);

        // 設置位置信息
        int startLine = cu.getLineNumber(annotation.getStartPosition());
        int endLine = cu.getLineNumber(annotation.getStartPosition() + annotation.getLength());
        annotationInfo.setStartLineNumber(startLine);
        annotationInfo.setEndLineNumber(endLine);
        annotationInfo.setStartPosition(annotation.getStartPosition());
        annotationInfo.setEndPosition(annotation.getStartPosition() + annotation.getLength());

        // 提取註解參數
        if (annotation instanceof NormalAnnotation) {
            NormalAnnotation normalAnnotation = (NormalAnnotation) annotation;
            extractNormalAnnotationParameters(normalAnnotation, annotationInfo);
        } else if (annotation instanceof SingleMemberAnnotation) {
            SingleMemberAnnotation singleMemberAnnotation = (SingleMemberAnnotation) annotation;
            extractSingleMemberAnnotationParameter(singleMemberAnnotation, annotationInfo);
        }

        return annotationInfo;
    }

    /**
     * 提取普通註解（有命名參數）的參數
     */
    private static void extractNormalAnnotationParameters(NormalAnnotation annotation, AnnotationInfo annotationInfo) {
        @SuppressWarnings("unchecked")
        List<MemberValuePair> pairs = annotation.values();

        for (MemberValuePair pair : pairs) {
            String parameterName = pair.getName().getIdentifier();
            String parameterValue = extractExpressionValue(pair.getValue());
            String parameterType = pair.getValue().getClass().getSimpleName();

            AnnotationInfo.AnnotationParameter parameter = new AnnotationInfo.AnnotationParameter(parameterName,
                    parameterValue, parameterType);
            annotationInfo.addParameter(parameter);
        }
    }

    /**
     * 提取單成員註解的參數
     */
    private static void extractSingleMemberAnnotationParameter(SingleMemberAnnotation annotation,
            AnnotationInfo annotationInfo) {
        String parameterValue = extractExpressionValue(annotation.getValue());
        String parameterType = annotation.getValue().getClass().getSimpleName();

        AnnotationInfo.AnnotationParameter parameter = new AnnotationInfo.AnnotationParameter("value", parameterValue,
                parameterType);
        annotationInfo.addParameter(parameter);
    }

    /**
     * 從表達式中提取值
     */
    private static String extractExpressionValue(Expression expression) {
        if (expression instanceof StringLiteral) {
            return ((StringLiteral) expression).getLiteralValue();
        } else if (expression instanceof TypeLiteral) {
            return ((TypeLiteral) expression).getType().toString();
        } else {
            return expression.toString();
        }
    }
}
