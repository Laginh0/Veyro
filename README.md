# Veyro

O Veyro é um ecossistema P2P para Android que conecta aparelhos próximos diretamente, sem exigir uma nuvem central para transportar os dados. Cada aparelho pode localizar, receber e enviar informações pela mesma interface, eliminando a separação entre emissor e receptor.

> Estado atual: **Alpha 0.1**. A versão está em desenvolvimento e deve ser usada para testes.

## Principais funcionalidades

| Recurso | O que faz |
| --- | --- |
| Conexão contínua | Mantém o aparelho visível e procurando outros dispositivos Veyro ao mesmo tempo. |
| Reconexão automática | Tenta restabelecer o vínculo quando um aparelho volta ao alcance. |
| Trust Hub | Guarda aparelhos conhecidos e permite configurar regras individuais de confiança. |
| Transferência de arquivos | Envia e recebe arquivos diretamente entre os aparelhos, com progresso e aprovação local. |
| Sincronização de bateria | Exibe carga e fonte de energia do aparelho conectado. |
| Notificações | Compartilha notificações autorizadas e permite dispensá-las remotamente. |
| Controle de mídia | Sincroniza o estado da reprodução e envia comandos de mídia. |
| Localizar aparelho | Aciona e encerra um alerta sonoro no dispositivo conectado. |
| Telefonia e SMS | Sincroniza o estado de chamadas e exige confirmação local antes de enviar um SMS remoto. |
| Compartilhamento de links | Envia endereços web para abertura no outro aparelho. |
| Comandos seguros | Oferece um conjunto limitado de ações remotas, como volume e lanterna. |
| Entrada remota | Executa gestos e insere texto quando o serviço de acessibilidade é autorizado pelo usuário. |

## Como funciona a conexão contínua

O Veyro usa o Google Nearby Connections com a estratégia `P2P_STAR`. Ao ativar o ecossistema, o aparelho fica visível para outros dispositivos Veyro e também inicia a detecção de aparelhos próximos.

Cada instalação recebe uma identidade persistente. A escolha do dispositivo central considera:

- nível de bateria;
- conexão com uma fonte de energia;
- capacidade de processamento disponível;
- identificador persistente para desempate.

O aparelho com menor pontuação inicia a conexão com o de maior pontuação. Em caso de empate, os identificadores determinam uma única direção. Um atraso determinístico entre 100 e 300 ms evita solicitações simultâneas concorrentes.

Depois da primeira confirmação por PIN, o Trust Hub reconhece o aparelho. Se a conexão cair, o serviço permanece ativo e procura restabelecê-la. Quando habilitado, o ecossistema também pode ser retomado após reinicialização do Android ou atualização do aplicativo.

## Modos de energia

O usuário pode escolher entre três comportamentos:

- **Contínuo:** prioriza disponibilidade e mantém o serviço preparado o tempo todo.
- **Equilibrado:** mantém a conexão contínua com uso mais moderado de bloqueios de energia.
- **Economia:** quando a tela está apagada, alterna janelas curtas de detecção com intervalos de repouso.

Durante uma transferência, o Veyro eleva temporariamente o tipo do serviço para sincronização de dados. Fora das transferências, utiliza apenas o serviço de dispositivo conectado.

## Segurança e privacidade

- A primeira conexão exige a comparação do mesmo PIN nos dois aparelhos.
- Arquivos de dispositivos desconhecidos aguardam aprovação local.
- As permissões do Trust Hub são configuradas separadamente para cada aparelho.
- Todo pedido remoto de SMS exige confirmação no aparelho que enviará a mensagem.
- A entrada remota aceita apenas comandos definidos pelo protocolo do Veyro.
- O serviço de acessibilidade não transmite o conteúdo da tela.
- A comunicação é direta entre os aparelhos através do Nearby Connections.

## Permissões

As permissões são solicitadas conforme os recursos utilizados:

| Permissão ou acesso | Finalidade |
| --- | --- |
| Bluetooth e aparelhos próximos | Localizar e conectar dispositivos Veyro. |
| Wi-Fi próximo | Negociar o transporte P2P disponível. |
| Notificações do sistema | Manter o serviço contínuo visível ao usuário. |
| Acesso às notificações | Sincronizar notificações autorizadas. |
| Política de notificações | Executar corretamente o alerta de localização. |
| Telefone, contatos e SMS | Sincronizar chamadas e processar pedidos de SMS com confirmação. |
| Câmera | Controlar a lanterna quando esse comando for solicitado. |
| Acessibilidade | Executar entrada remota autorizada. |

Negar uma permissão opcional não impede a transferência básica de arquivos.

## Requisitos

- Android 5.0 ou superior (`minSdk 21`).
- Google Play Services com suporte ao Nearby Connections.
- Bluetooth e Wi-Fi disponíveis.
- Android Studio ou JDK 17 para compilar o projeto.

## Compilação

No Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

O APK será gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Instalação por ADB

```powershell
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

Se já existir uma versão assinada com outra chave, o Android exigirá a desinstalação anterior. A desinstalação apaga as configurações locais e os aparelhos salvos no Trust Hub.

## Testes

O projeto inclui:

- testes unitários da identidade, eleição e protocolo;
- testes instrumentados dos serviços e preferências Android;
- verificação estática com Android Lint;
- validação manual em Android 16.

## Estrutura do projeto

```text
app/src/main/java/com/veyro/p2p/
├── features/       Recursos de bateria, mídia, notificações, telefonia e controle remoto
├── nearby/         Descoberta, conexão, eleição e transferência P2P
├── permissions/    Permissões do Android
├── protocol/       Mensagens trocadas entre os aparelhos
├── service/        Serviço contínuo em primeiro plano
├── settings/       Trust Hub, identidade e modos de energia
├── storage/        Persistência dos arquivos recebidos
└── ui/             Tema e componentes visuais
```

## Release atual

A versão disponível para testes é a [Veyro Alpha 0.1](https://github.com/Laginh0/Veyro/releases/tag/v0.1.0-alpha).

O APK da Alpha usa uma assinatura de desenvolvimento. Antes de atualizar, confirme se a instalação existente foi assinada pela mesma chave.

## Limitações da Alpha

- A interface e o protocolo ainda podem mudar antes da versão estável.
- Otimizações agressivas de bateria de alguns fabricantes podem interromper serviços em segundo plano.
- A validação completa entre diferentes fabricantes e versões do Android ainda está em andamento.
- Esta versão não deve ser distribuída como uma versão de produção.
