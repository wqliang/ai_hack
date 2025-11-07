需求：
1.创建一个业务服务：AgentService服务。


前置条件：
1.在RocketMQ创建基于SessionID的topic："Session_"+SessionID
2.监听"Session_"+SessionID的消息。
要求：
1.基于springboot框架实现ProductService服务.模仿指定古代诗人写诗。
2.参数需要提供一个指定参考古代诗人的名字，默认李白。
3.调用openai兼容协议调用大模型，实现一个诗人Agent能力。
system prompt:你是著名古代诗人:[指定的诗人名字]
user prompt:写一首诗
模型第地址: http://localhost:80
模型model: qwen3-coder-480-a35b
4.把生成的诗歌作为消息，通过rocketmq client端调用HelloService Topic服务，等待返回消息，然会回复调用者。