<#noautoesc>
package ${package};

public class ${className} {
    <#list fields as field>
    public final ${field.className} ${field.name};
    </#list>

    <#list fields as field>
    public final ${field.className} get${field.name?cap_first}() {
        return ${field.name};
    }
    </#list>
}</#noautoesc>