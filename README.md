<div align="center">

# CRAVO

**Minecraft Bedrock, do seu jeito.**

Launcher para Android com foco em **PvP**, **personalização** e uma experiência mais simples.

[Downloads](https://github.com/vinicius353/cravo-launcher/releases) · [Desenvolvimento](https://github.com/vinicius353/cravo-launcher/actions) · [Reportar problema](https://github.com/vinicius353/cravo-launcher/issues)

</div>

---

## Sobre

O **CRAVO Launcher** é um projeto independente para Minecraft Bedrock no Android, desenvolvido a partir da base aberta do LeviLauncher.

A proposta é reunir ferramentas de gerenciamento e personalização em uma interface minimalista, com atenção especial à experiência de jogadores PvP.

> **Status:** em desenvolvimento. As ferramentas planejadas para PvP e otimização ainda não devem ser consideradas implementadas ou validadas.

## Objetivos

- **PvP:** controles e opções de personalização voltados à jogabilidade competitiva.
- **Personalização:** evoluir a interface e as configurações do launcher.
- **Desempenho:** investigar melhorias e oferecer ajustes testados, sem prometer aumento de FPS.
- **Simplicidade:** organizar recursos importantes de maneira clara.

## Requisitos e instalação

A base atual do aplicativo utiliza:

- **Android 9 ou superior** (API 28+).
- Dispositivo **ARM64**.
- Uma cópia legítima do **Minecraft Bedrock** instalada pela Google Play.

O suporte real a dispositivos e versões do Minecraft depende de testes. Para verificar builds de desenvolvimento, acesse [GitHub Actions](https://github.com/vinicius353/cravo-launcher/actions) e procure um artifact de APK nas execuções concluídas com sucesso.

**Atenção:** builds de desenvolvimento podem apresentar falhas e não representam uma versão estável. A página [Releases](https://github.com/vinicius353/cravo-launcher/releases) será usada para versões publicadas oficialmente.

## Desenvolvimento

Para compilar o projeto a partir do código-fonte:

```bash
git clone --recurse-submodules https://github.com/vinicius353/cravo-launcher.git
cd cravo-launcher
./gradlew assembleDebug
```

São necessários **JDK 21**, Android SDK, NDK e as dependências configuradas pelo Gradle. A compilação também pode exigir configurações de serviços externos; confira os workflows e logs caso falhe.

Contribuições e relatos de bugs são bem-vindos nas [Issues](https://github.com/vinicius353/cravo-launcher/issues).

---

## Créditos e licença

O **CRAVO Launcher** é uma adaptação independente baseada em [LeviLauncher para Android (LeviLaunchroid)](https://github.com/LiteLDev/LeviLaunchroid), desenvolvido pelo projeto [LeviMC / LiteLDev](https://github.com/LiteLDev) e seus [contribuidores](https://github.com/LiteLDev/LeviLaunchroid/graphs/contributors). A base original e seus autores mantêm seus respectivos créditos e direitos.

O código é distribuído sob a **Apache License 2.0**, conforme o arquivo [LICENSE](LICENSE). Avisos de licença, copyright e atribuição presentes no código-fonte devem ser preservados conforme os termos aplicáveis.

**Minecraft** é uma marca da Mojang Studios/Microsoft. O CRAVO não é afiliado, endossado ou patrocinado por Mojang ou Microsoft.
