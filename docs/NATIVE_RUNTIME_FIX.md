# Investigação ARM64 — experimental

Base: feature/cravo-alpha031-full-revamp, 1f2f409bad4de09b21ad72e179e8ddf8be5cabfd.

## Evidência no código
O inspetor lê entradas ZIP. O extrator também encontra essas entradas, porém a
implementação anterior apagava o arquivo extraído quando NativeImageGuard falhava,
interrompia as demais bibliotecas daquele APK e engolia a exceção. A verificação
posterior emitia CRAVO_NATIVE_LIB_MISSING. Assim, presença no APK não implicava
presença no cache. Não temos a exceção original do aparelho para afirmar que essa
foi a única causa do relato.

## Alterações
- Preparar em arquivo temporário gravável, proteger somente após preparação e
  publicar por rename atômico. Falha não destrói cache previamente válido.
- Propagar a causa de extração/preparação com APK, entrada ZIP e destino; não
  reinterpretar uma falha de transformação como ausência no APK.
- Unificar ABI do processo entre ApplicationInfo, extrator e inspetor. Converter
  arm64-v8a para arm64 somente ao formar o diretório, nunca a entrada ZIP.
- Inspetor usa getStorageProfileId e exibe o caminho absoluto do cache e de cada APK.
- Enviar os logs do extrator ao ProgressListener da tela de carregamento.
- Invalidar manifestos anteriores (extractor=4).
- Não modificar APKs importados nem copiar biblioteca de outra arquitetura.

## Validação
Testes JVM exercitam publicação a partir de APK sintético, preservação dos bytes do
APK, falha de preparação preservando cache e rejeição de arquivo vazio. Não
simulam Android linker nem NativeImageGuard nativo. Actions compila e roda esses
casos; sucesso de CI não comprova inicialização do Minecraft.

Sem teste em aparelho nesta etapa. Após instalar: abrir diagnóstico, copiar paths,
iniciar a mesma instância e registrar CRAVO_NATIVE_PREPARE_FAILED, se houver,
incluindo sua causa. Não é necessário reimportar o APK para invalidar o cache.

## Auditoria visual pendente
CravoHubActivity ainda abre InstancesActivity e ContentManagementActivity;
CravoWorkspaceActivity abre SettingsActivity. Essas telas e seus diálogos precisam
ser migrados preservando seleção, backup, edição e opções técnicas. A migração
completa não faz parte deste primeiro patch de runtime. CravoTheme já centraliza o
accent; vermelho em escolhas de paleta e estados de erro não deve ser removido
por substituição textual indiscriminada.
