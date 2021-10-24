package dawin.york.testtask


import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.KotlinModule
import lombok.extern.slf4j.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.awaitBody
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient


@SpringBootApplication
class TesttaskApplication

fun main(args: Array<String>) {
    runApplication<TesttaskApplication>(*args)
}

enum class EventType(val str: String) {
    MESSAGE("message_new"),
    CONFIRMATION("confirmation")
}

//There are a lot of classes because vk api documentation doesn't match to real responses

data class server(
    val server_id: Int
)

data class Response(
    val response: server
)

data class MessageEvent(
    @JsonProperty("type")
    val type: String,
    @JsonProperty("object")
    val message: MessageWrapper?,
    @JsonProperty("group_id")
    val groupId: Int?
)

data class MessageWrapper(
    @JsonProperty("message")
    val message: Message
)


data class Message(
    @JsonProperty("id")
    val id: Int?,
    @JsonProperty("date")
    val date: Long?,
    @JsonProperty("peer_id")
    val peer_id: Int?,
    @JsonProperty("from_id")
    val from_id: Int?,
    @JsonProperty("text")
    val text: String?,
    @JsonProperty("random_id")
    val randomId: Int?
)

data class Result(
    @JsonProperty("response")
    val response: String
)

@Configuration
@PropertySource("classpath:vk_secret.yml")
data class Env(
    @Value("\${vk.config.group_id}")
    val groupId: Int,

    @Value("\${vk.config.token}")
    val accessToken: String,

    @Value("\${vk.config.api_url}")
    val vkApiUrl: String,

    @Value("\${vk.config.confiramtion_string}")
    val code: String,

    @Value("\${vk.config.method_send}")
    val method: String,

    @Value("\${vk.config.version}")
    val version: String
)

interface CallBackAPI<T, R> {
    fun onMessage(message: T): Mono<R>
}

@Configuration
@EnableWebFlux
class VKConfig(
    @Autowired
    val env: Env
) {

    private val CONTENT_TYPE_HEADER = "Content-Type"


    @Bean
    fun webClient() = WebClient.builder()
        .baseUrl(env.vkApiUrl)
        .defaultHeader(CONTENT_TYPE_HEADER, MediaType.APPLICATION_JSON.toString())
        .clientConnector(ReactorClientHttpConnector(HttpClient.create().wiretap(true)))
        .build()

    @Bean
    fun objectMapperBuilder(): Jackson2ObjectMapperBuilder =
        Jackson2ObjectMapperBuilder().modulesToInstall(KotlinModule())
}

@RestController
@Slf4j
class VkCallbackAPI(
    @Autowired
    val env: Env,

    @Autowired
    private val webClient: WebClient
) : CallBackAPI<Message, Result> {
    private val prefixUrl = "/method/"
    private val logger = LoggerFactory.getLogger(VkCallbackAPI::class.java)


    /*
       I thought that I could register callback server in post construct method but I couldn't do it because
       Fucking confirmation code doesn't be got without user
    */
//    @PostConstruct
//    private fun registerCallBack() {
//        webClient.post()
//            .uri{uriBuilder ->
//                uriBuilder.path("/method/"+env.method)
//                    .queryParam("access_token", env.accessToken)
//                    .queryParam("v", "5.131")
//                    .queryParam("group_id", env.groupId)
//                    .queryParam("title", "singleton")
//                    .queryParam("url", env.ownUrl)
//                    .build()
//            }
//            .accept(MediaType.APPLICATION_JSON)
//            .retrieve()
//            .onStatus(HttpStatus::is4xxClientError){Mono.error(RuntimeException("4XX Error ${it.statusCode()}"))}
//            .onStatus(HttpStatus::is5xxServerError){Mono.error(RuntimeException("5XX Error ${it.statusCode()}"))}
//            .bodyToMono(Response::class.java)
//            .map
//
//    }

    @PostMapping("/onMessage")
    fun onEvent(@RequestBody event: MessageEvent): Mono<ResponseEntity<String>> {
        logger.info(event.toString())
        if (event.type == EventType.CONFIRMATION.str) {
            return Mono.just(ResponseEntity(env.code, HttpStatus.OK))
        } else if (event.type == EventType.MESSAGE.str) {
            return onMessage(event.message!!.message)
                .map { ResponseEntity("ok", HttpStatus.OK) }

        } else {
            return Mono.just(ResponseEntity("event type doesn't support", HttpStatus.UNPROCESSABLE_ENTITY))
        }
    }

//     AGA
//    @PreDestroy
//    private fun unsubscribeCallBack() {
//
//    }

    override fun onMessage(message: Message): Mono<Result> {
        return webClient.post()
            .uri { uriBuilder ->

                    val builder = uriBuilder.path(prefixUrl + env.method)
                        .queryParam("access_token", env.accessToken)
                        .queryParam("v", env.version)
                        .queryParam("group_id", env.groupId)
                        .queryParam("peer_id", message.from_id)
                    if (message.text != null)
                        builder.queryParam("message", "You said:"+message.text)
                    builder.queryParam("random_id", message.randomId)
                    builder.build()

            }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
                // nice api returns errors with 200 OK
//            .onStatus(HttpStatus::is4xxClientError) { Mono.error(RuntimeException("4XX Error ${it.statusCode()}")) }
//            .onStatus(HttpStatus::is5xxServerError) { Mono.error(RuntimeException("5XX Error ${it.statusCode()}")) }
            .bodyToMono(Result::class.java)
    }
}


