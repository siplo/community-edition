<#assign uriConfig = config.scoped["UriTemplate"]["uri-templates"]>
Alfresco.constants.URI_TEMPLATES = {
<#list uriConfig.childrenMap["uri-template"] as t>
   ${t.attributes["id"]}: "${t.value}"<#if t_has_next>,</#if>
</#list>
}
