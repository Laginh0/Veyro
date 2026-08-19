package com.veyro.p2p.ui.i18n

import com.veyro.p2p.settings.AppLanguage

object VeyroI18n {
    private val english = mapOf(
        "Ecossistema" to "Ecosystem",
        "Recursos" to "Features",
        "Definições" to "Settings",
        "Configurações" to "Settings",
        "Sobre" to "About",
        "ECOSSISTEMA" to "ECOSYSTEM",
        "RECURSOS" to "FEATURES",
        "DEFINIÇÕES" to "SETTINGS",
        "CONFIGURAÇÕES" to "SETTINGS",
        "Abrir menu" to "Open menu",
        "NAVEGAÇÃO" to "NAVIGATION",
        "Ecossistema ativo" to "Ecosystem active",
        "Pronto para conectar" to "Ready to connect",
        "Conectar novo aparelho" to "Connect a new device",
        "Seu ecossistema" to "Your ecosystem",
        "Seu ecossistema Veyro" to "Your Veyro ecosystem",
        "Aparelhos próximos e atividades em uma única visão." to "Nearby devices and activity in one view.",
        "Conecte seus aparelhos diretamente, sem internet, com você no controle de cada acesso." to "Connect your devices directly, without internet, while keeping control of every permission.",
        "Configurar meu ecossistema" to "Set up my ecosystem",
        "Permissões necessárias" to "Permissions required",
        "Autorize dispositivos próximos e notificações para continuar." to "Allow nearby devices and notifications to continue.",
        "A Veyro usa Bluetooth e Wi-Fi local para localizar outros aparelhos " to "Veyro uses Bluetooth and local Wi-Fi to find other devices ",
        "e transferir arquivos diretamente, sem enviar sua localização. " to "and transfer files directly without sharing your location. ",
        "Agora não" to "Not now",
        "Próxima" to "Continue",
        "Radar" to "Radar",
        "Em espera" to "Standby",
        "Sempre ativo" to "Always active",
        "Visível" to "Visible",
        "Procurando aparelhos" to "Finding devices",
        "Conectado" to "Connected",
        "Conectando" to "Connecting",
        "Atenção" to "Attention",
        "Este aparelho" to "This device",
        "Aparelho conectado" to "Connected device",
        "Inicie uma busca para revelar aparelhos próximos" to "Start the ecosystem to reveal nearby devices",
        "Ativar ecossistema contínuo" to "Enable continuous ecosystem",
        "Ecossistema contínuo ativo" to "Continuous ecosystem active",
        "Ecossistema contínuo ativo; aguardando aparelhos próximos." to "Continuous ecosystem active; waiting for nearby devices.",
        "Este aparelho está visível e procurando ao mesmo tempo." to "This device is visible and finding others at the same time.",
        "Desativar" to "Disable",
        "Atividade recente" to "Recent activity",
        "Tudo tranquilo" to "All clear",
        "As atividades do ecossistema aparecerão aqui." to "Ecosystem activity will appear here.",
        "Atenção necessária" to "Action required",
        "Transferência de arquivo" to "File transfer",
        "Em andamento" to "In progress",
        "aguardando sua aprovação" to "waiting for your approval",
        "Salvando no aparelho" to "Saving on device",
        "Recebido e salvo" to "Received and saved",
        "Transferência concluída" to "Transfer complete",
        "Falha na transferência" to "Transfer failed",
        "Transferência cancelada" to "Transfer canceled",
        "Nova notificação" to "New notification",
        "Bateria remota" to "Remote battery",
        "Ações conectadas" to "Connected actions",
        "Arquivos, mídia, comandos e continuidade entre aparelhos." to "Files, media, commands, and continuity across devices.",
        "Conecte um aparelho" to "Connect a device",
        "Os recursos aparecem aqui assim que uma conexão segura for confirmada no Ecossistema." to "Features appear here after a secure connection is confirmed in the Ecosystem.",
        "Controle e privacidade" to "Control and privacy",
        "Revise os acessos usados por cada recurso do ecossistema." to "Review the permissions used by each ecosystem feature.",
        "Central de controle" to "Control center",
        "Escolha como cada parte do seu ecossistema deve funcionar." to "Choose how each part of your ecosystem should work.",
        "Recursos do ecossistema" to "Ecosystem features",
        "CONTINUIDADE" to "CONTINUITY",
        "MÍDIA E COMUNICAÇÃO" to "MEDIA AND COMMUNICATION",
        "ACESSO REMOTO" to "REMOTE ACCESS",
        "Transferência de arquivos" to "File transfer",
        "Envie, receba e aprove arquivos entre aparelhos." to "Send, receive, and approve files between devices.",
        "Estado da bateria" to "Battery status",
        "Compartilhe carga e fonte de energia durante a conexão." to "Share charge and power-source status during a connection.",
        "Relatório de conectividade" to "Connectivity report",
        "Compartilhe transporte, internet, rede limitada e sinal disponível." to "Share transport, internet access, metered status, and available signal strength.",
        "Ping P2P" to "P2P ping",
        "Meça periodicamente a latência direta entre os aparelhos." to "Periodically measure direct latency between devices.",
        "Links compartilhados" to "Shared links",
        "Envie links que só abrem após um toque no destino." to "Send links that open only after a tap on the destination device.",
        "Sincronizar notificações" to "Sync notifications",
        "Mostre e descarte notificações do aparelho conectado." to "Show and dismiss notifications from the connected device.",
        "Acompanhe e controle a reprodução remotamente." to "Monitor and control media playback remotely.",
        "Chamadas e SMS" to "Calls and SMS",
        "Sincronize eventos e confirme localmente cada SMS." to "Sync events and locally confirm every SMS.",
        "Encontrar aparelho" to "Find device",
        "Permita solicitar um alarme no aparelho conectado." to "Allow an alarm request on the connected device.",
        "Ações remotas seguras" to "Safe remote actions",
        "Controle volume e lanterna com comandos nativos." to "Control volume and flashlight with native commands.",
        "Mouse e teclado remotos" to "Remote mouse and keyboard",
        "Use este aparelho como touchpad e teclado." to "Use this device as a touchpad and keyboard.",
        "Pasta remota compartilhada" to "Shared remote folder",
        "Exponha somente uma pasta escolhida pelo seletor seguro do Android." to "Expose only a folder chosen with Android's secure picker.",
        "Sincronização de contatos" to "Contact sync",
        "Ofereça contatos selecionados e confirme cada importação." to "Offer selected contacts and confirm every import.",
        "Modo de apresentação" to "Presentation mode",
        "Controle slides, tela preta e cronômetro." to "Control slides, blackout, and timer.",
        "Mesa digitalizadora" to "Drawing tablet",
        "Transmita stylus, pressão, inclinação e botão principal." to "Transmit stylus, pressure, tilt, and primary button.",
        "Idioma" to "Language",
        "Escolha o idioma da interface do Veyro." to "Choose the Veyro interface language.",
        "Português" to "Portuguese",
        "Inglês" to "English",
        "Energia e rádio" to "Power and connectivity",
        "Escolha quanto tempo o Veyro pode manter o ecossistema ativo." to "Choose how long Veyro may keep the ecosystem active.",
        "Contínuo" to "Continuous",
        "Equilibrado" to "Balanced",
        "Economia" to "Battery saver",
        "Mantém o processador disponível durante toda a sessão." to "Keeps the processor available throughout the session.",
        "Mantém o wakelock apenas durante transferências." to "Keeps the wake lock only during transfers.",
        "Além disso, encerra buscas ociosas quando a tela apaga." to "Also pauses idle detection when the screen turns off.",
        "Cada aparelho só recebe os privilégios que você ativar." to "Each device receives only the privileges you enable.",
        "Nenhum aparelho confirmado" to "No confirmed devices",
        "Depois de confirmar o PIN de uma conexão, o aparelho aparecerá aqui com todos os privilégios desativados." to "After confirming a connection PIN, the device appears here with every privilege disabled.",
        "Conectado agora" to "Connected now",
        "Aparelho conhecido" to "Known device",
        "Remover" to "Remove",
        "Salvar arquivos automaticamente" to "Save files automatically",
        "Sem pedir confirmação local a cada recebimento." to "Without asking for local confirmation for every incoming file.",
        "Permitir localizar este aparelho" to "Allow finding this device",
        "Autoriza o outro aparelho a tocar o alarme remoto." to "Allows the other device to sound the remote alarm.",
        "Acessos das novas funções" to "Feature permissions",
        "Notificações" to "Notifications",
        "Ativar acesso às notificações" to "Enable notification access",
        "Configurar acesso às notificações" to "Configure notification access",
        "Modos/Não Perturbe" to "Modes/Do Not Disturb",
        "Ativar acesso a modos" to "Enable mode access",
        "Configurar acesso a modos" to "Configure mode access",
        "Telefonia e SMS" to "Calls and SMS",
        "Permitir telefonia e SMS" to "Allow calls and SMS",
        "Usar Veyro no lugar do identificador atual" to "Use Veyro instead of the current caller ID app",
        "Permitir lanterna remota" to "Allow remote flashlight",
        "Ativar controle remoto" to "Enable remote control",
        "O Veyro pode sincronizar nome e número. Eventos só são compartilhados durante uma conexão; todo SMS remoto exige confirmação local." to "Veyro can synchronize caller name and number. Events are shared only during a connection; every remote SMS requires local confirmation.",
        "Sem o acesso de identificação de chamadas, o Veyro sincroniza apenas o estado da chamada, sem nome ou número. Todo SMS remoto exige confirmação local." to "Without caller ID access, Veyro synchronizes only the call state, without a name or number. Every remote SMS requires local confirmation.",
        "Princípios da conexão" to "Connection principles",
        "Direta" to "Direct",
        "A comunicação ocorre entre os aparelhos, sem nuvem." to "Communication happens between devices without a cloud service.",
        "Confirmada" to "Confirmed",
        "Um PIN igual nos dois aparelhos valida cada conexão." to "A matching PIN on both devices validates each connection.",
        "Temporária" to "Temporary",
        "Encerrar a sessão interrompe o canal e as sincronizações." to "Ending the session stops the channel and synchronization.",
        "O que deseja fazer?" to "What would you like to do?",
        "Enviar arquivos" to "Send files",
        "Receber arquivos" to "Receive files",
        "Aguardando destinatário" to "Waiting for recipient",
        "Este aparelho está visível" to "This device is visible",
        "A busca está ativa." to "Device detection is active.",
        "Aguardando o outro aparelho." to "Waiting for the other device.",
        "Aguardando o outro aparelho..." to "Waiting for the other device...",
        "Criando conexão segura" to "Creating secure connection",
        "Solicitando conexão" to "Requesting connection",
        "Confirme a identidade" to "Confirm identity",
        "Validando segurança" to "Validating security",
        "Compare o PIN exibido nos dois aparelhos." to "Compare the PIN shown on both devices.",
        "Canal P2P pronto." to "P2P channel ready.",
        "Sincronização direta ativa." to "Direct synchronization active.",
        "Confirmar PIN de segurança" to "Confirm security PIN",
        "Aceite somente se os dois aparelhos mostrarem exatamente o mesmo PIN." to "Accept only if both devices show exactly the same PIN.",
        "PIN confere" to "PIN matches",
        "Bateria do outro aparelho" to "Other device battery",
        "Aguardando a primeira atualização segura..." to "Waiting for the first secure update...",
        "Usando a bateria" to "On battery",
        "Conectividade do outro aparelho" to "Other device connectivity",
        "Aguardando o primeiro relatório de rede..." to "Waiting for the first network report...",
        "Internet disponível" to "Internet available",
        "Sem acesso à internet" to "No internet access",
        "Rede limitada" to "Metered network",
        "Rede não limitada" to "Unmetered network",
        "Internet disponível • Rede limitada" to "Internet available • Metered network",
        "Internet disponível • Rede não limitada" to "Internet available • Unmetered network",
        "Sem acesso à internet • Rede limitada" to "No internet access • Metered network",
        "Sem acesso à internet • Rede não limitada" to "No internet access • Unmetered network",
        "Latência do canal Nearby" to "Nearby channel latency",
        "Aguardando resposta do ping..." to "Waiting for a ping response...",
        "Medição de ida e volta pelo canal P2P." to "Round-trip measurement over the P2P channel.",
        "Conectividade remota" to "Remote connectivity",
        "Rede móvel" to "Mobile network",
        "Sem rede" to "No network",
        "Outra rede" to "Other network",
        "Desconhecida" to "Unknown",
        "Controle de mídia" to "Media control",
        "Aguardando o estado de mídia do outro aparelho." to "Waiting for media state from the other device.",
        "Nenhuma sessão de mídia ativa no outro aparelho." to "No active media session on the other device.",
        "Mídia sem título" to "Untitled media",
        "Erro de reprodução" to "Playback error",
        "Notificações sincronizadas" to "Synchronized notifications",
        "Ative o acesso local para compartilhar e descartar notificações." to "Enable local access to share and dismiss notifications.",
        "Nenhuma notificação recebida do outro aparelho." to "No notifications received from the other device.",
        "Descartar no outro aparelho" to "Dismiss on other device",
        "Encontrar meu dispositivo" to "Find my device",
        "Faz o outro aparelho tocar no volume de alarme." to "Makes the other device ring at alarm volume.",
        "Fazer tocar" to "Make it ring",
        "Eventos do outro aparelho" to "Events from the other device",
        "Nenhuma chamada ou SMS sincronizado nesta conexão." to "No call or SMS synchronized in this connection.",
        "Chamada recebida" to "Incoming call",
        "Chamada perdida" to "Missed call",
        "SMS recebido" to "SMS received",
        "Mensagem SMS" to "SMS message",
        "Número de destino" to "Destination number",
        "Número desconhecido" to "Unknown number",
        "O envio remoto nunca é automático: o outro aparelho recebe uma notificação para confirmar ou recusar." to "Remote sending is never automatic: the other device receives a notification to approve or reject.",
        "Solicitar envio no outro aparelho" to "Request sending on other device",
        "Ações remotas seguras" to "Safe remote actions",
        "Somente ações nativas desta lista são aceitas; comandos shell são sempre bloqueados." to "Only native actions from this list are accepted; shell commands are always blocked.",
        "Ligar lanterna" to "Turn on flashlight",
        "Volume +" to "Volume +",
        "Volume −" to "Volume −",
        "Enviar comando" to "Send command",
        "Compartilhar link" to "Share link",
        "Somente HTTP/HTTPS. O link abre apenas após um toque no aparelho de destino." to "HTTP/HTTPS only. The link opens only after a tap on the destination device.",
        "Enviar link" to "Send link",
        "Controle remoto" to "Remote control",
        "Arraste para mover o cursor virtual; toque ou toque duas vezes para clicar." to "Drag to move the virtual cursor; tap or double tap to click.",
        "Touchpad Veyro" to "Veyro touchpad",
        "Rolar ↑" to "Scroll ↑",
        "Rolar ↓" to "Scroll ↓",
        "Início" to "Home",
        "Digitar no outro aparelho" to "Type on other device",
        "Texto para o campo focado" to "Text for the focused field",
        "Selecionar arquivo" to "Select file",
        "Nome, tamanho e tipo são enviados antes do Payload.FILE." to "Name, size, and type are sent before Payload.FILE.",
        "Aguardando metadados..." to "Waiting for metadata...",
        "Arquivo temporário; aguardando salvamento definitivo." to "Temporary file; waiting for final save.",
        "Disponível em Downloads/Veyro." to "Available in Downloads/Veyro.",
        "Comandos recebidos" to "Received commands",
        "Nenhum comando recebido." to "No commands received.",
        "Cliente Nearby pronto" to "Nearby client ready",
        "Cliente Nearby inicializado." to "Nearby client initialized.",
        "Rádios locais autorizados." to "Local connectivity authorized.",
        "Falha ao iniciar Nearby" to "Failed to start Nearby",
        "Não foi possível inicializar o Nearby." to "Nearby could not be initialized.",
        "Dispositivo pronto" to "Device ready",
        "Aguardando conexão automática..." to "Waiting for automatic connection...",
        "Nenhum aparelho encontrado ainda." to "No devices found yet.",
        "Outro aparelho Veyro pode encontrá-lo." to "Another Veyro device can find it.",
        "Ecossistema contínuo ativo; reconexão automática habilitada." to "Continuous ecosystem active; automatic reconnection enabled.",
        "Ativando visibilidade e detecção simultâneas..." to "Enabling visibility and detection simultaneously...",
        "Pedido enviado; aguardando o outro aparelho..." to "Request sent; waiting for the other device...",
        "PIN confirmado. Aguardando o outro aparelho..." to "PIN confirmed. Waiting for the other device...",
        "Arquivo recebido recusado; nada foi salvo." to "Incoming file declined; nothing was saved.",
        "Ecossistema ativo; aguardando aparelhos próximos..." to "Ecosystem active; waiting for nearby devices...",
        "Compare o PIN nos dois aparelhos." to "Compare the PIN on both devices.",
        "Aparelho fora de alcance; procurando reconexão..." to "Device out of range; looking for a reconnection...",
        "O outro aparelho foi desconectado." to "The other device was disconnected.",
        "Comando enviado para o outro aparelho." to "Command sent to the other device.",
        "Pedido para localizar aparelho enviado." to "Find-device request sent.",
        "Pedido enviado; o outro aparelho precisará confirmar o SMS." to "Request sent; the other device must confirm the SMS.",
        "Link enviado; o outro aparelho deverá tocar para abri-lo." to "Link sent; the other device must tap it to open.",
        "Ative o acesso às notificações para controlar mídia." to "Enable notification access to control media.",
        "Autorize este aparelho nas Definições para permitir o alarme remoto." to "Authorize this device in Settings to allow the remote alarm.",
        "Alarme de localização ativo neste aparelho." to "Find-device alarm active on this device.",
        "Ative o acesso às notificações para permitir o descarte." to "Enable notification access to allow dismissal.",
        "Nenhuma mídia ativa no outro aparelho." to "No active media on the other device.",
        "Chamada recebida no outro aparelho." to "Incoming call on the other device.",
        "Chamada perdida no outro aparelho." to "Missed call on the other device.",
        "SMS recebido no outro aparelho." to "SMS received on the other device.",
        "Ative o serviço de acessibilidade do Veyro para receber controles." to "Enable the Veyro accessibility service to receive controls.",
        "Controle remoto indisponível neste aparelho." to "Remote control unavailable on this device.",
        "Escolha o destino dos controles e dados exibidos abaixo." to "Choose the target for the controls and data shown below.",
        "Selecione um contato no Android. Fotos não são enviadas e toda importação exige confirmação local." to "Select a contact in Android. Photos are not sent, and every import requires local confirmation.",
        "Selecionar e oferecer contato" to "Select and offer contact",
        "Contato sem nome" to "Unnamed contact",
        "Importar" to "Import",
        "Cronômetro parado" to "Timer stopped",
        "Zerar cronômetro" to "Reset timer",
        "Anterior" to "Previous",
        "Próximo" to "Next",
        "Parar" to "Stop",
        "Iniciar" to "Start",
        "Restaurar tela" to "Restore screen",
        "Tela preta" to "Blackout",
        "A área transmite posição, pressão, inclinação e o botão principal do stylus." to "The pad transmits position, pressure, tilt, and the stylus primary button.",
        "Toque ou use uma caneta" to "Touch or use a stylus",
        "Borracha" to "Eraser",
        "Toque" to "Touch",
        "Ponteiro" to "Pointer",
        "Acesso remoto a arquivos" to "Remote file access",
        "Nenhuma pasta local exposta. O restante do armazenamento permanece inacessível." to "No local folder is exposed. The rest of storage remains inaccessible.",
        "Escolher pasta" to "Choose folder",
        "Parar acesso" to "Stop access",
        "Abrir pasta remota" to "Open remote folder",
        "Voltar" to "Back",
        "Pasta" to "Folder",
        "Arquivo" to "File",
        "Contato recebido; confirme antes de importar." to "Contact received; confirm before importing.",
        "Importação recusada neste aparelho." to "Import declined on this device.",
        "Contato oferecido; aguardando confirmação no outro aparelho." to "Contact offered; waiting for confirmation on the other device.",
        "Comando de apresentação enviado." to "Presentation command sent.",
        "Compartilhamento da pasta encerrado." to "Folder sharing stopped.",
        "Solicitação segura de pasta enviada." to "Secure folder request sent.",
        "A pasta remota está vazia." to "The remote folder is empty.",
        "Solicitação de arquivo enviada." to "File request sent.",
        "Tela preta solicitada pela apresentação remota." to "Blackout requested by the remote presentation.",
        "Tela preta ativa • toque para sair" to "Blackout active • tap to exit",
        "Tela preta encerrada localmente." to "Blackout ended locally.",
        "Apresentação remota em andamento." to "Remote presentation in progress.",
        "Apresentação remota encerrada." to "Remote presentation stopped.",
        "Conteúdo da pasta compartilhada enviado." to "Shared folder contents sent.",
        "Arquivo indisponível ou fora da pasta compartilhada." to "File unavailable or outside the shared folder.",
        "Solicitação de arquivo recusada com segurança." to "File request safely declined.",
        "Não foi possível salvar o arquivo recebido." to "The incoming file could not be saved.",
        "Erro inesperado na conexão Nearby." to "Unexpected Nearby connection error.",
        "Conectando ao serviço de transferência..." to "Connecting to the transfer service...",
        "Aguarde o serviço de transferência ficar pronto." to "Wait for the transfer service to become ready.",
        "Acesso a modos concedido neste aparelho." to "Mode access granted on this device.",
        "Conceda acesso a modos para este aparelho tocar mesmo em Não Perturbe." to "Grant mode access so this device can ring even during Do Not Disturb.",
        "Enviado" to "Sent",
        "Recebido" to "Received",
        "Recusar" to "Decline",
        "Estado" to "State",
        "concluído" to "completed",
        "em andamento" to "in progress",
        "salvo em Downloads/Veyro" to "saved in Downloads/Veyro",
        "Aguarde..." to "Please wait...",
        "Veyro aguardando destinatário" to "Veyro waiting for recipient",
        "Veyro procurando aparelhos" to "Veyro finding devices",
        "Ecossistema Veyro ativo" to "Veyro ecosystem active",
        "Veyro conectando" to "Veyro connecting",
        "Veyro conectado" to "Veyro connected",
        "Veyro em segundo plano" to "Veyro running in background",
        "Transferências Veyro" to "Veyro transfers",
        "Mostra conexões e transferências P2P em andamento." to "Shows active P2P connections and transfers.",
        "Transferência P2P ativa." to "P2P transfer active.",
        "Encerrar" to "Stop",
        "Sobre o Veyro" to "About Veyro",
        "Conexões diretas, privadas e sob o seu controle." to "Direct, private connections under your control.",
        "Feito para o seu ecossistema" to "Made for your ecosystem",
        "O Veyro conecta seus aparelhos localmente para compartilhar arquivos, estados e controles sem depender de uma nuvem." to "Veyro connects your devices locally to share files, status, and controls without relying on a cloud service.",
        "Privacidade por padrão" to "Privacy by default",
        "Cada conexão é confirmada por PIN e cada recurso pode ser desligado individualmente." to "Every connection is confirmed by PIN, and each feature can be disabled individually.",
        "Preferências de recursos atualizadas." to "Feature preferences updated."
    )

    fun translate(text: String, language: AppLanguage): String {
        if (language == AppLanguage.PORTUGUESE || text.isBlank()) return text
        english[text]?.let { return it }
        return translateDynamic(text)
    }

    private fun translateDynamic(text: String): String = when {
        Regex("""\d+ de \d+ ativos""").matches(text) -> {
            val values = Regex("""\d+""").findAll(text).map { it.value }.toList()
            "${values[0]} of ${values[1]} enabled"
        }
        text.startsWith("Notificações: ") ->
            "Notifications: " + translateAccessStatus(text.removePrefix("Notificações: "))
        text.startsWith("Modos/Não Perturbe: ") ->
            "Modes/Do Not Disturb: " + translateAccessStatus(text.removePrefix("Modos/Não Perturbe: "))
        text.startsWith("Telefonia e SMS: ") ->
            "Calls and SMS: " + translateAccessStatus(text.removePrefix("Telefonia e SMS: "))
        text.startsWith("Veyro como identificador: ") ->
            "Veyro as caller ID: " + translateAccessStatus(text.removePrefix("Veyro como identificador: "))
        text.startsWith("Lanterna remota: ") ->
            "Remote flashlight: " + translateAccessStatus(text.removePrefix("Lanterna remota: "))
        text.startsWith("Controle remoto: ") ->
            "Remote control: " + translateAccessStatus(text.removePrefix("Controle remoto: "))
        text.startsWith("Compare este código com ") ->
            "Compare this code with " + text.removePrefix("Compare este código com ")
        text.startsWith("Conectado a ") -> "Connected to " + text.removePrefix("Conectado a ")
        text.startsWith("Aparelhos conectados (") -> "Connected devices " + text.removePrefix("Aparelhos conectados ")
        text.startsWith("Enviado por ") -> "Sent by " + text.removePrefix("Enviado por ")
        text.startsWith("Cronômetro: ") -> "Timer: " + text.removePrefix("Cronômetro: ")
        text.startsWith("Remoto: ") -> "Remote: " + text.removePrefix("Remoto: ")
            .replace(" • tela preta", " • blackout")
        text.startsWith("Stylus • pressão ") -> "Stylus • pressure " + text.removePrefix("Stylus • pressão ")
        text.startsWith("Borracha • pressão ") -> "Eraser • pressure " + text.removePrefix("Borracha • pressão ")
        text.startsWith("Toque • pressão ") -> "Touch • pressure " + text.removePrefix("Toque • pressão ")
        text.startsWith("Ponteiro • pressão ") -> "Pointer • pressure " + text.removePrefix("Ponteiro • pressão ")
        text.startsWith("Pasta local compartilhada: ") ->
            "Shared local folder: " + text.removePrefix("Pasta local compartilhada: ")
        text.startsWith("Enviando ") -> "Sending " + text.removePrefix("Enviando ")
        text.startsWith("Falha ao transferir ") ->
            "Failed to transfer " + text.removePrefix("Falha ao transferir ")
        text.startsWith("Falha ao salvar ") ->
            "Failed to save " + text.removePrefix("Falha ao salvar ")
        text.endsWith(" aguarda sua aprovação para ser salvo.") ->
            text.removeSuffix(" aguarda sua aprovação para ser salvo.") + " is waiting for your approval before being saved."
        text.endsWith(" salvo em Downloads/Veyro.") ->
            text.removeSuffix(" salvo em Downloads/Veyro.") + " saved in Downloads/Veyro."
        text.startsWith("Estado: ") -> "State: " + text.removePrefix("Estado: ")
        text.startsWith("Estado ") -> "State " + text.removePrefix("Estado ")
        text.startsWith("Último resultado: ") -> "Latest result: " + text.removePrefix("Último resultado: ")
        text.startsWith("Em andamento: ") -> "In progress: " + text.removePrefix("Em andamento: ")
        text.startsWith("Conectado à energia • ") -> "Plugged in • " + text.removePrefix("Conectado à energia • ")
        text.endsWith(" transferidos") -> text.removeSuffix(" transferidos") + " transferred"
        text.endsWith(" aparelho(s) encontrado(s).") ->
            text.removeSuffix(" aparelho(s) encontrado(s).") + " device(s) found."
        text.endsWith(" aparelho(s) no ecossistema próximo.") ->
            text.removeSuffix(" aparelho(s) no ecossistema próximo.") + " device(s) in the nearby ecosystem."
        text.endsWith(" aparelho(s) conectado(s).") ->
            text.removeSuffix(" aparelho(s) conectado(s).") + " device(s) connected."
        text.endsWith(" aparelho(s) ainda conectado(s).") ->
            text.removeSuffix(" aparelho(s) ainda conectado(s).") + " device(s) still connected."
        text.endsWith(" item(ns) na pasta compartilhada.") ->
            text.removeSuffix(" item(ns) na pasta compartilhada.") + " item(s) in the shared folder."
        text.startsWith("Bateria remota: ") -> "Remote battery: " + text.removePrefix("Bateria remota: ")
        text.startsWith("Conectividade remota: ") ->
            "Remote connectivity: " + text.removePrefix("Conectividade remota: ")
        text.endsWith(" • com internet") ->
            text.removeSuffix(" • com internet") + " • with internet"
        text.endsWith(" • sem internet") ->
            text.removeSuffix(" • sem internet") + " • no internet"
        text.startsWith("Sinal informado: ") ->
            "Reported signal: " + text.removePrefix("Sinal informado: ")
        text.startsWith("Mídia remota: ") -> "Remote media: " + text.removePrefix("Mídia remota: ")
        text.startsWith("Notificação recebida de ") ->
            "Notification received from " + text.removePrefix("Notificação recebida de ")
        text.startsWith("Comando recebido de ") ->
            "Command received from " + text.removePrefix("Comando recebido de ")
        text.startsWith("Negociando conexão com ") ->
            "Negotiating connection with " + text.removePrefix("Negociando conexão com ")
        text.startsWith("Regras de ") && text.endsWith(" atualizadas.") ->
            "Rules for " + text.removePrefix("Regras de ").removeSuffix(" atualizadas.") + " updated."
        else -> text
    }

    private fun translateAccessStatus(status: String): String = when (status) {
        "ativo" -> "enabled"
        "pendente" -> "pending"
        "inativo" -> "disabled"
        "inativo (opcional)" -> "disabled (optional)"
        else -> status
    }
}
