<html>
<head>
   <title>Upload success</title>
</head>
<body>
<#if (args.success?exists)>
   <script type="text/javascript">
      ${args.success}
   </script>
</#if>
</body>
</html>