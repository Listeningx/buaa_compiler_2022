import java.util.ArrayList;
import java.util.List;

public class Short_circuit {
    List<MidCode> midCodes = new ArrayList<>();
    StringBuilder midCodeOutput = new StringBuilder();

    int while_num = -1;
    int if_num = -1;
    int tmp_num = -1;
    private String while_begin_label(){
        return "while_begin_" + ++while_num;
    }
    private String while_end_label(){
        return "while_end_" + while_num;
    }
    private String if_end_label(){
        return "if_end_" + ++if_num;
    }
    private String else_end_label(){
        return "else_end_" + if_num;
    }

    public void before_analyse_while_stmt(TreeNode t){
        String beginLabel = while_begin_label();
        String endLabel = while_end_label();
        MidCode midCode = new MidCode("label",null,beginLabel,t.symbol_table,t.occur_line);//res = beginLabel
        this.midCodes.add(midCode);
        this.midCodeOutput.append(beginLabel + ":\n");
        t.label_begin = beginLabel;
        t.label_end = endLabel;

    }
    public void before_analyse_if_stmt(TreeNode t){
        String if_end_label = if_end_label();
        String else_end_label = else_end_label();
        t.label_begin = if_end_label;
        t.label_end = else_end_label;
    }

    public Short_circuit(List<MidCode> midCodes,StringBuilder midCodeOutput){
        this.midCodes = midCodes;
        this.midCodeOutput = midCodeOutput;
    }

}
class CondExp{
    String op = "";
    TreeNode left = null;
    TreeNode right = null;

    public void CondMidCode(TreeNode t,List<MidCode> midCodes,StringBuilder midCodeOutput){
        for(TreeNode t1 : t.childNodes){
            CondMidCode(t1,midCodes,midCodeOutput);
        }
        assert t.subCond;
        midCodeOutput.append(t.midCodeOutput_hidden);
        midCodes.addAll(t.midCodes_hidden);
    }

    public void jumpWhenFalse(String targetLabel,List<MidCode> midCodes,StringBuilder midCodeOutput,Symbol_Table symbol_table,int line,int num){
        if(left!=null&&left.ident == null) left.ident = String.valueOf(left.value);
        if(right!=null&&right.ident == null) right.ident = String.valueOf(right.value);
        if(op.equals("&&")){
            assert left != null;
            if(!left.logic){//不是逻辑表达式
                CondMidCode(left,midCodes,midCodeOutput);
                MidCode midCode = new MidCode("jump",null,left.ident,symbol_table,line);//res = left.ident
                midCode.operand_2 = targetLabel;
                midCode.result = 0;
                midCodeOutput.append("if " + left.ident + " == 0 jump to " +targetLabel + "\n");
                midCodes.add(midCode);
            }else{
                left.condExp.jumpWhenFalse(targetLabel, midCodes, midCodeOutput,left.symbol_table,left.occur_line,++num);
            }

            if(!right.logic){
                CondMidCode(right,midCodes,midCodeOutput);
                MidCode midCode = new MidCode("jump",null,right.ident,symbol_table,line);//res = right.ident
                midCode.operand_2 = targetLabel;
                midCode.result = 0;
                midCodeOutput.append("if " + right.ident + " == 0 jump to " +targetLabel + "\n");
                midCodes.add(midCode);
            }else{
                right.condExp.jumpWhenFalse(targetLabel,midCodes,midCodeOutput,right.symbol_table,right.occur_line,++num);
            }
            return;
        }

        if(op.equals("||")){
            String tmp_label = "label_" + line + "_" + num + "_" + left.ident + "_" + right.ident;

            assert left != null;
            if(!left.logic){
                CondMidCode(left,midCodes,midCodeOutput);
                MidCode midCode = new MidCode("jump",null,left.ident,symbol_table,line);//res = left.ident
                midCode.operand_2 = tmp_label;
                midCode.result = 1;
                midCodeOutput.append("if " + left.ident + " == 1 jump to " +tmp_label + "\n");
                midCodes.add(midCode);
            }else{
                left.condExp.jumpWhenTrue(tmp_label,midCodes,midCodeOutput,left.symbol_table,left.line,++num);
            }

            if(!right.logic){
                CondMidCode(right,midCodes,midCodeOutput);
                MidCode midCode = new MidCode("jump",null,right.ident,symbol_table,line);//res = right.ident
                midCode.operand_2 = tmp_label;
                midCode.result = 1;
                midCodeOutput.append("if " + right.ident + " == 1 jump to " +tmp_label + "\n");
                midCodes.add(midCode);
            }else{
                right.condExp.jumpWhenTrue(tmp_label,midCodes,midCodeOutput,right.symbol_table, right.line, ++num);
            }

            //跳转到targetLabel
            MidCode m = new MidCode("jump_without_condition",null,targetLabel,symbol_table,line);//res = label
            midCodes.add(m);
            midCodeOutput.append("j " + targetLabel + "\n");
            //加入这个临时标签tmpLabel
            MidCode m1 = new MidCode("label",null,tmp_label,symbol_table,line);//res = tmplabel
            midCodes.add(m1);
            midCodeOutput.append(tmp_label + ":\n");
            return;

        }



        //符号不是|| && , 取对偶符号，为真则跳转
       String thisOp = reverseOp(op);
        if(op.equals("bottom")){
            CondMidCode(left,midCodes,midCodeOutput);

            MidCode midCode = new MidCode("jump",null,left.ident,symbol_table,line);//res = left.ident
            midCode.operand_2 = targetLabel;
            midCode.result = 0;
            midCodeOutput.append("if " + left.ident + " == 0 jump to " +targetLabel + "\n");
            midCodes.add(midCode);
        }else{
            CondMidCode(left,midCodes,midCodeOutput);
            CondMidCode(right,midCodes,midCodeOutput);

            MidCode m = new MidCode(thisOp,symbol_table,line);
            m.operand_1 = left.ident;
            m.operand_2 = right.ident;
            m.res = targetLabel;
            midCodes.add(m);
            midCodeOutput.append("if " + left.ident + " " + thisOp + " " +right.ident + " jump to " + targetLabel + "\n");
        }

    }

    public void jumpWhenTrue(String targetLabel,List<MidCode> midCodes,StringBuilder midCodeOutput,Symbol_Table symbol_table,int line,int num){
        if(left!=null&&left.ident == null) left.ident = String.valueOf(left.value);
        if(right!=null&&right.ident == null) right.ident = String.valueOf(right.value);

        if(op.equals("&&")){
            String tmp_label = "label_" + line + "_" + num + "_" +  "_" + left.ident + "_" + right.ident;

            assert left != null;
            if(!left.logic){
                CondMidCode(left,midCodes,midCodeOutput);

                MidCode midCode = new MidCode("jump",null,left.ident,symbol_table,line);//res = left.ident
                midCode.operand_2 = tmp_label;
                midCode.result = 0;
                midCodeOutput.append("if " + left.ident + " == 0 jump to " +tmp_label + "\n");
                midCodes.add(midCode);
            }else{
                left.condExp.jumpWhenFalse(tmp_label,midCodes,midCodeOutput,symbol_table,line,++num);
            }

            if(!right.logic){
                CondMidCode(right,midCodes,midCodeOutput);

                MidCode midCode = new MidCode("jump",null,right.ident,symbol_table,line);//res = right.ident
                midCode.operand_2 = tmp_label;
                midCode.result = 0;
                midCodeOutput.append("if " + right.ident + " == 0 jump to " +tmp_label + "\n");
                midCodes.add(midCode);
            }else{
                right.condExp.jumpWhenFalse(tmp_label,midCodes,midCodeOutput,symbol_table,line,++num);

            }

            //跳转到targetLabel
            MidCode m = new MidCode("jump_without_condition",null,targetLabel,symbol_table,line);//res = label
            midCodes.add(m);
            midCodeOutput.append("j " + targetLabel + "\n");
            //加入这个临时标签tmpLabel
            MidCode m1 = new MidCode("label",null,tmp_label,symbol_table,line);//res = tmplabel
            midCodes.add(m1);
            midCodeOutput.append(tmp_label + ":\n");

            return;
        }

        if(op.equals("||")){
            if(!left.logic){
                CondMidCode(left,midCodes,midCodeOutput);

                MidCode midCode = new MidCode("jump",null,left.ident,symbol_table,line);//res = left.ident
                midCode.operand_2 = targetLabel;
                midCode.result = 1;
                midCodeOutput.append("if " + left.ident + " == 1 jump to " +targetLabel + "\n");
                midCodes.add(midCode);
            }else{
                left.condExp.jumpWhenTrue(targetLabel,midCodes,midCodeOutput,symbol_table,line,++num);
            }

            if(!right.logic){
                CondMidCode(right,midCodes,midCodeOutput);

                MidCode midCode = new MidCode("jump",null,right.ident,symbol_table,line);//res = right.ident
                midCode.operand_2 = targetLabel;
                midCode.result = 1;
                midCodeOutput.append("if " + right.ident + " == 1 jump to " +targetLabel + "\n");
                midCodes.add(midCode);
            }else{
                right.condExp.jumpWhenTrue(targetLabel,midCodes,midCodeOutput,symbol_table,line,++num);
            }
            return;
        }



        String thisOp = op;
        if(op.equals("bottom")){
            CondMidCode(left,midCodes,midCodeOutput);

            assert left != null;
            MidCode midCode = new MidCode("jump",null,left.ident,symbol_table,line);//res = left.ident
            midCode.operand_2 = targetLabel;
            midCode.result = 1;
            midCodeOutput.append("if " + left.ident + " == 1 jump to " +targetLabel + "\n");
            midCodes.add(midCode);
        }else{
            if(thisOp.equals("==")) thisOp = "===";
            CondMidCode(left,midCodes,midCodeOutput);
            CondMidCode(right,midCodes,midCodeOutput);

            MidCode m = new MidCode(thisOp,symbol_table,line);
            m.operand_1 = left.ident;
            m.operand_2 = right.ident;
            m.res = targetLabel;
            midCodes.add(m);
            midCodeOutput.append("if " + left.ident + " " + thisOp + " " +right.ident + " jump to " + targetLabel + "\n");
        }
    }

    public String reverseOp(String op){
        String thisOp = "";
        if(op.equals("==")){
            thisOp = "!=";
        }else if(op.equals("!=")){
            thisOp = "===";//因为==被用于储存函数返回值，所以用===表示相等
        }else if(op.equals(">=")){
            thisOp = "<";
        }else if(op.equals("<=")){
            thisOp = ">";
        }else if(op.equals(">")){
            thisOp = "<=";
        }else if(op.equals("<")){
            thisOp = ">=";
        }
        return thisOp;
    }


}