package kai.javaparser.ast.java2ast;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;

import kai.javaparser.ast.java2ast.handler.ControlFlowHandler;
import kai.javaparser.ast.java2ast.handler.HandlerContext;
import kai.javaparser.ast.java2ast.handler.InvocationHandler;
import kai.javaparser.model.AnnotationInfo;
import kai.javaparser.model.MethodGroup;
import kai.javaparser.model.SequenceDiagramData;
import kai.javaparser.util.AnnotationExtractor;

/**
 * EnhancedInteractionModelVisitor is a visitor that extracts interaction models
 * from a Java AST.
 * 
 * It extends ASTVisitor to traverse the AST and extract interaction models.
 * 
 * It also handles control flow fragments and method groups.
 * 
 * This class has been refactored to act as a dispatcher that delegates
 * specific node handling to specialized handlers.
 */
public class EnhancedInteractionModelVisitor extends ASTVisitor {
    private final HandlerContext context;
    private final ControlFlowHandler controlFlowHandler;
    private final InvocationHandler invocationHandler;

    public EnhancedInteractionModelVisitor(SequenceDiagramData sequenceData, CompilationUnit compilationUnit) {
        this.context = new HandlerContext(sequenceData, compilationUnit);
        this.controlFlowHandler = new ControlFlowHandler();
        this.invocationHandler = new InvocationHandler();
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        // 記錄當前類名 (包含包名)
        String packageName = context.getCompilationUnit().getPackage() != null
                ? context.getCompilationUnit().getPackage().getName().getFullyQualifiedName()
                : "";
        context.setCurrentClassName(packageName.isEmpty() ? node.getName().getIdentifier()
                : packageName + "." + node.getName().getIdentifier());
        return true;
    }

    @Override
    public void endVisit(TypeDeclaration node) {
        context.setCurrentClassName(null); // 離開類別範圍
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        // 創建新的方法分組
        MethodGroup currentMethodGroup = new MethodGroup();

        // 檢查是否為構造函數（沒有返回類型且方法名與類名相同）
        boolean isConstructor = node.getReturnType2() == null;
        String methodName = node.getName().getIdentifier();
        String className = context.getCurrentClassName();
        String simpleClassName = className != null ? className.substring(className.lastIndexOf('.') + 1) : "Unknown";

        // 如果是構造函數，方法名應該是類名
        if (isConstructor) {
            currentMethodGroup.setMethodName(simpleClassName);
        } else {
            currentMethodGroup.setMethodName(methodName);
        }

        currentMethodGroup.setClassName(className);
        currentMethodGroup.setFullMethodName(className + "." +
                (isConstructor ? simpleClassName : methodName));
        currentMethodGroup.setStartLineNumber(context.getCompilationUnit().getLineNumber(node.getStartPosition()));

        // 提取方法簽名
        StringBuilder signature = new StringBuilder();
        if (node.parameters() != null) {
            for (int i = 0; i < node.parameters().size(); i++) {
                SingleVariableDeclaration param = (SingleVariableDeclaration) node.parameters().get(i);
                if (i > 0)
                    signature.append(", ");
                // Use resolveTypeBinding for full qualified name if available
                ITypeBinding typeBinding = param.getType().resolveBinding();
                signature.append(typeBinding != null ? typeBinding.getQualifiedName() : param.getType().toString());
            }
        }
        currentMethodGroup.setMethodSignature(signature.toString());

        // 新增：提取 throws 異常
        List<String> thrownExceptions = new ArrayList<>();
        for (Object exceptionType : node.thrownExceptionTypes()) {
            if (exceptionType instanceof Type) {
                ITypeBinding typeBinding = ((Type) exceptionType).resolveBinding();
                thrownExceptions
                        .add(typeBinding != null ? typeBinding.getQualifiedName() : ((Type) exceptionType).toString());
            }
        }
        currentMethodGroup.setThrownExceptions(thrownExceptions);

        // 新增：提取方法註解
        List<AnnotationInfo> methodAnnotations = AnnotationExtractor.extractAnnotations(
                node.modifiers(), context.getCompilationUnit());
        for (AnnotationInfo annotation : methodAnnotations) {
            currentMethodGroup.addAnnotation(annotation);
        }

        // 添加到序列數據
        context.getSequenceData().addMethodGroup(currentMethodGroup);
        context.setCurrentMethodGroup(currentMethodGroup);

        return true;
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        if (context.getCurrentMethodGroup() != null) {
            // 設置方法結束行號
            context.getCurrentMethodGroup()
                    .setEndLineNumber(
                            context.getCompilationUnit().getLineNumber(node.getStartPosition() + node.getLength()));
            context.setCurrentMethodGroup(null);
        }
    }

    // 委派控制流節點給 ControlFlowHandler
    @Override
    public boolean visit(IfStatement node) {
        return controlFlowHandler.visit(node, context);
    }

    @Override
    public void endVisit(IfStatement node) {
        controlFlowHandler.endVisit(node, context);
    }

    @Override
    public boolean visit(ForStatement node) {
        return controlFlowHandler.visit(node, context);
    }

    @Override
    public void endVisit(ForStatement node) {
        controlFlowHandler.endVisit(node, context);
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        return controlFlowHandler.visit(node, context);
    }

    @Override
    public void endVisit(EnhancedForStatement node) {
        controlFlowHandler.endVisit(node, context);
    }

    @Override
    public boolean visit(WhileStatement node) {
        return controlFlowHandler.visit(node, context);
    }

    @Override
    public void endVisit(WhileStatement node) {
        controlFlowHandler.endVisit(node, context);
    }

    // 委派方法呼叫節點給 InvocationHandler
    @Override
    public boolean visit(MethodInvocation node) {
        return invocationHandler.visit(node, context);
    }

    @Override
    public void endVisit(MethodInvocation node) {
        invocationHandler.endVisit(node, context);
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        return invocationHandler.visit(node, context);
    }

    @Override
    public void endVisit(ConstructorInvocation node) {
        invocationHandler.endVisit(node, context);
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        return invocationHandler.visit(node, context);
    }

    @Override
    public void endVisit(ClassInstanceCreation node) {
        invocationHandler.endVisit(node, context);
    }
}