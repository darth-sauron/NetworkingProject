A project created for Networking classes during my second year of studies at uni (the Client class was provided by a teacher) :

//A Distributed Database Protocol

It is a database distributed across many nodes. Each node is essentially a process on a server, which contains the data
(<key, value>) and all of those nodes are connected via at least one other node to the whole network of nodes. A client
wishing to retrieve the data, information about data or to modify the data in any way connects to one of the nodes,
sends the operation to be executed, and that node, in turn, does the work for the client of traversing the network and
executing the requested operation.

//Setting Up the Network

First, the nodes need to be created and key and values need to be stored in them. In addition to that, (unless it is the
first node created) they need to be connected to at least one other node in the network. It can be done from a command
line in the next manner:

javac DatabaseNode.java

java DatabaseNode -tcpport <TCP port number> -record <key>:<value> [ -connect <address>:<port> ]

It is necessary to have one space between the individual terms as shown above. The "functions" should be prefaced with a
dash "-" like so. Following is the detailed explanation of those "functions":

-tcpport number (e.g -tcpport 9991):
creates the process running on this server (like local host) with the specified port number, that then will be used for
connecting to this process.

-record number:number (e.g -record 30:450):
puts the data in this new node the key being the first number before the ":" and the value being the one after.

-connect IPaddress:portNumber (e.g -connect localhost:9991):
it is considered as optional because the first node created cannot connect to any other node as they do not exist yet.
For the rest of the nodes, however, it is mandatory to connect to at least one other node to be a part of the whole
network. Can be a few of -connect IPaddreaa:portNumber in the same command line as it creates a list of all the
nodes this node will have to connect to.

As a result of this a process will be created, which consists of two threads, one of them listens on a TCP port and the
other one is listening to a UDP port of the same port number. Each of them is responsible for a different functionality,
which will be discussed further in the description of communication and protocol.

After everything is set up correctly the processes will be running, waiting for a client and/or another node to connect.


//Making Queries In the Database

In order to make queries it is sufficient to create a client which will send the request to a node it is connected to.
It can be done from a command line in the following manner:

javac DatabaseClient.java

java DatabaseClient -gateway <address>:<TCP port number> -operation <operation> <parameter/s>

Just like in the setting up of a node it is crucial to have the spaces and the dashes "-" in the above indicated places.

Here are the descriptions of the "functions":

-gateway IPaddress:portNumber (e.g -gateway localhost:9991)
connects to one of the process located on this address and listening to this portNumber.

-operation operation [parameter:[parameter2]] (e.g -operation get-min, -operation set-value 14:987):
specifies the operation the client wants to see executed.
Accepted operations with their description are as follows:

[set-value key:value]:
accepts two parameters, the key that contains the value that should be changed and the value to which the former one
needs to be changed to. It returns the "ERROR" message if the specified key was not found, else it returns the "OK"
message if the value at this key was successfully changed.

[get-value key]:
accepts one parameter, the key, which value the client wishes to see. It returns the "ERROR" message if no such key is
contained within the database, else it returns the value in the form of "key:value".

[find-key key]:
accepts one parameter, the key which the client wishes to see the process containing it. It returns the "ERROR" message
if no such key is found, else it returns the address and the port number of the process containing it in the form of
"address:port".

[get-max]:
no parameters are needed, as it looks for the maximum value within the database. It returns the maximum value in the
form of "key:value".

[get-min]:
no parameters are needed, as it looks for the minimum value within the database. It returns the minimum value in the
form of "key:value".

[new-record key:value]:
accepts two parameters, the new key and the new value for the node to which this client is connected. It returns the
"OK" message once the replacement on the node is complete.

[terminate]:
no parameters are needed, as its goal is to terminate the work of the process to which the client is connected. It
returns the "OK" message once the process has successfully disconnected from the network.

After the operation has been specified the client waits on the node's response.

//The Communication: Node to Node

The first communication that happens among the nodes is when they are getting connected to each other for the first
time. It occurs over TCP as follows:

The node that was created has a list of the nodes it needs to be connected to supplied by the one setting up the node
from the command line (as described above). As part of its initialization it contacts each of those nodes iteratively,
sending first line that reads "Hello". This message is followed by a new line that reads "Connect:" and its address,
column (":"), and then its port number (the address is passed in a dotted decimal notation as it will be important for
the next steps of the implementation of this database). After that it sends an empty line to let the node know it is
done. It then waits for the response from this node, which is in effect the doted decimal notation of the address of
this node (which then replaces the one, which already exists in the list, it is a precaution against having names of
addresses in the list). Once it receives an empty line from this node the communication is closed.

On the receiving end of this communication, a node sees that its TCP port has been contacted with the word "Hello", it
then waits for the next line which will contain "Connect:" with the address and the port separated by a column. It takes
those values and stores them in its list of "contacts", i.e. it'll know that it is its "neighbour" now. Once it gets an
empty line it sends this node its address in doted decimal notation following the line containing the words "This is my
actual address: ". It then sends an empty line and thus the "introductory" communication is concluded.

When a node is disconnected from the network, after the "terminate" operation it follows a similar pattern, being also
conducted over TCP:

The node that is being disconnected sends to all the nodes in its list of "contacts" the same message iteratively. It
sends the first line "Goodbye", and the next line is "Disconnect:", after which come the address and the port of this
network, separated by a column ":". After that it sends an empty line, to signify the end of communication and closes
the connection.

On the receiving end of it, a node sees that its TCP port has been contacted with "Goodbye", so it reads the second line
where it gets the credentials of this network and using this information removes this node from its list of "contacts".
The communication is then over.

There are 3 more first lines (which is essentially how the node differentiates between its tasks) that a node may
receive to its TCP port from other nodes.

One of them is "MAX". A node, to which a client is connected and requests the max value (using the get-max operation),
waits for "candidates" to this position from its "neighbours", (who in turn get it from their "neighbours", but more on
that later on, in the UDP part of the implementation). At first, it stores its own value (and key) as the max. Once it
receives the "MAX" message from one of its nodes, it reads the next line and stores it as an int (to then compare it
with one of the keys in its map of the clients' requests and answers, which will be further discussed in the client-node
communication). It then reads the next line which contains both the "candidate" for the max value (key:value) and the
list of nodes which were visited already in order to avoid contacting them again. All of these things are separated by
a column ":" (e.g key:value:nodeAddress|nodePort-nodeAddress|nodePort). After retrieving the value (using split(":") on
a String) it compares it to the one already located in its map of requests associated with the key that it has recieved
in the second line. If the value there is smaller that the one freshly recieved it is replaced by this new value.

The one which starts with "MIN" works exactly the same as the "MAX" one, except for the part where it is not looking for
the max value but a minimum one.

And lastly it may receive just a plain answer from one of its "neighbours" to one of the operations it has recieved from
a client. It follows a very similar structure to the "MAX" and "MIN" tasks. It is also something a node to which a
client is connected receives. It first receives a line with a number, which it stores as an integer (which is a key of
one of the requests) and the next line it receives is the answer to that request. It then replaces the value stored with
this key with this new value it just receives (the value was initially "ERROR", more details on that in the client-node
communication).

And accordingly, TCP also serves the nodes that are directly connected to the node, which recieved the request from the
client, to send the answer to the request. It follows a similar structure in the three cases of get-max, get-min and the
operations requiring finding things associated with a particular key. For both min and max operations it sends the first
line as "MIN" or "MAX" respectively, followed by a new line, containing the key of this request and the last line with
the "candidate" for the aforementioned position and the list of the nodes that have been visited already. All of which
are separated by a column. It then sends an empty line and closes the connection.
It is mostly the same in case of finding the requested attribute of the key, except the first line of such consists of
the key of the request only, and the rest follows the same pattern.

As for the UDP connection between the nodes it is as follows:
When a node gets a request from a client and does not have the appropriate response itself it sends a message on top of
UDP to all of its "contacts" requesting the needed answer.

For every client function it has a specific word (in all CAPS) which it sends as the beginning of the message it sends
to its "contacts". For [set-value] it is "SET", for [get-value] - "GET", [find-key] - "FIND", [get-max] - "MAX",
[get-min] - "MIN". These words are followed by a parameter (or parameters joined by ":" with no spaces in between) after
a space, then after space comes the port number of this node joined by ":" with its address and then another space and
the key of the request.

General rules of the single UDP line:
"FUNCTION parameter:s port:address[-port:address] key [address|port[-address:port]]"

And then it waits for 5 seconds to see if it will get a response in that time to its TCP port
in order not to ask more nodes than needed in case the attribute of the key was already found or in case of the min and
max functions, in order not to miss any "candidates" for this role. It basically works like broadcasting its request to
as many "neighbours" as needed for the completion of the task. The nodes receiving those messages, check the first word
of the line and proceed accordingly. As an example if the first word if "SET":
1) First and foremost, no matter what is the first word of the line it checks the third word, which contains all the
nodes that have been visited already, takes the last one visited and stores it in case it has successfully completed the
task, and it is now time to turn back. It also creates a new list of visited nodes, which does not include the one it
has taken in order to send it as the next list of visited values so that the next node can do the same (in a way like
retracing the steps backwards by the same road that was taken to get there).
2) It checks the next word after the space, splits its parameter into two (key and value) and checks the key for
which this operation needs to be executed. It then compares it to the key it has and proceeds accordingly:
*If it has this key it sets its value to the value it found in the parameters. It then checks if there are more than one
nodes that were visited beforehand (if this list contains "-" it means there are more than one, it will be obvious why
in the next steps). If not it takes it upon itself to contact the node (the original node as it were) using TCP to send
it the result (in this case "OK"). If there are more than one it changes the first word of the line to "DONE" and the
next word (instead of parameter) becomes "OK" (or if it was get or find functions it would be the key:value or the
address:port respectively) it then adds the modified list of visited files from the first step and finally the key of
the request. This line then becomes the message it sends to the node that came before it (also from the first step)
*If it does not have that key it simply "propagates" the same request to its "neighbors" iteratively (unless they are on
the list of the nodes that have been visited already which is checked by this node (it checks if the port:address of its
"neighbor" is contained in the third word of the line (the list)). The only modification the node does to the original
request is adding its port number with ":" and its address after a dash "-" to the list of visited nodes in the third
word. The remaining nodes do the same.

It is more or less identical to the GET and FIND, as once the key is found there is no need to look further (even though
because of the peculiarities of the code it is likely that more nodes than necessary will get the "broadcast" and
therefore return an answer to the asking node which will not store it anywhere as it will have removed the client
from its "clients" list (map) after it sent it its answer (explanation in client-node protocol)). It is a little
different in the case of MAX and MIN, since it is only possible to know those extremes after traversing the
whole database. But it is also very unnecessary to visit any node more than once. In this scenario the node that has
been contacted by the client acts as a sort of "root" of the "tree" and it goes through its "leaves", which in turn go
through theirs. Every node in the list of connections of the node that has been contacted by the client acts as a sort
of beginning of a branch. The traversing of this "branch" stops when one of the nodes on the path "has no one else to
contact" because all of its "neighbors" are already on the list of the nodes visited.
Only once no node is left that wasn't "contacted" the search stops and the current max/min value is
submitted as the "candidate". And so the line is the same for the most part as for the SET, GET and FIND except for the
fact that it's longer (one word more) as it'll contain the list of nodes already visited in this format:
nodeAddress|nodePort[-nodeAddress|nodePort]. Connected to those functions are the functions OVER_MAX and OVER_MIN, which
are sent back (like in the step 2 of SET the DONE function) and the node sends it when it doesn't have any more
"neighbors" to contact and so it sends what it "thinks" is the maximum/minimum as the "candidate" backwards. Once the
node receives OVER_MAX, OVER_MIN or DONE all it has to do is to keep sending it backwards until it gets to the node that
requested the service in the first place (and that is done over TCP as described above). It checks if the node that
comes before it is the "root" by checking if the list of nodes that came before contains a "-". If not, then that's the
"initial node".

This is it for the communication between nodes.

//The Communication: Client-Node

This communication happens exclusively over TCP. The client connects to a node it chooses and sends it one of the
operations specified in the beginning og this file. It then waits for the response. In the meantime the node checks the
operation and checks if there are parameter/s to store. Then in most operations it creates a key for this client to
store it in its map of clients <keyOfClient:answerToQuery>. If the operation is terminate or new-record there is no need
to put anything in the map since these are "local" actions and require no specialized response. These are the simplest
actions and so here are their protocols:
When the operation is terminate the node contacts all of its "neighbors" iteratively and sends the "Goodbye" message to
them (as described in the Node-Node communication). After it's complete it sends back "OK" to the client, followed by
an empty line and exits.
When the operation is new-record the node takes the parameters it stored before and (they are passed as key:value and so
the node splits the string over ":") and replaces its own with them. After this is done it sends "OK" to the client and
and then an empty line, ending the communication.
Now, for the functions that involve searching for a specific key (i.e set-value, get-value and find-key). First the node
puts the key its generated for this client into its map of "clients" and sets its value to "ERROR". It then checks if
its key corresponds to the one requested (in the parameter/s it got from the client). If yes it simply sends the
requested information to the client (or "OK" in case of set-value). Else it checks if its list of connections is empty
(in case it's the only node in the database). If it is, it sends "ERROR" back to the client followed by an empty line
terminating the connection. If not it contacts all of its "contacts" iteratively (as described above). After each
iteration it checks if the value associated with the given client has been updated. If yes it exits the loop and sends
the new response to the client, followed by an empty line and ends the communication. if the value wasn't updated while
inside the loop but after the client will still receive this answer. If not it'll receive "ERROR" that the value was
initialized with. After that it removes this client from its map.
Finally in case of get-max or get-min operations the node doesn't get any parameters and puts the client's key into the
"clients" map with the value it stores. It then contacts all of its "neighbors" iteratively (as described above) and
from time it gets "candidates" from the nodes as to what the max/min could be. It compares it to the value already
associated with the key of the client and if it is bigger/smaller it replaces it with the new value. When all the nodes
have been visited it sends the client the final value associated with its key, followed by an empty line and closes the
connection.

//To Put the Pieces Together

The described protocols will be now illustrated with the following function from beginning to end: get-min.
(to describe it better, the objects in question might be slightly anthropomorphised for the convenience of
explaining)

get-min:

A client is connected to a node and sends it an operation "get-min" over TCP. This node reads the operation, sees that
there are no parameters and proceeds to execute the operation. It first checks if it has any contacts in its list of
"connections". If it does not it sends its key and its value to the client as the minimum ones. If it does, it creates
a key for this client and puts it into a map of "clients" with the value(and the key) it stores as well as the list of
nodes already visited (for now only itself), all of which are separated by ":". It then contacts every contact it has
(unless it is on the list that have been visited already stored in the map, in which case it skips it) and sends them a
message on top of UDP into which it puts the following things: MIN as the first word, then after a space it puts the
currently min value and the key of this value. Then another space, and it attaches its address and port, after that
another space and the key of the client and finally another space and the list of nodes already visited followed by
"\r\n". And then it waits for 5 seconds (could be modified to be more for bigger databases). In the meantime, its first
neighbor gets a "MIN" message to its UDP port. It first splits the query by spaces and inspects the third word. It
checks if it contains "-". If it does not it just registers the address and the port number found in that server as the
return address and port number in case it doesn't have any other connections but the node that contacted it. In case
there is a "-" it creates two strings for two different cases: one called "attach" in case there are more connections to
send the request to and attachBack in case there are no more connections, and it wants to send the answer back the way
the request arrived. Attach contains all the nodes that came before exaclty like the list of nodes it recieved (might
seem redundant, but it helps visually when creating a new query). AttachBack has everything but the last node on that
list. This last node will be the destination address and destination port number in case it needs to send the response
back. It then checks if its value is smaller than the one contained in the "parameters" section of the message (second
word). If yes it registers its key and value as the new parameters. It then adds to the last word (the list of visited
nodes its own address and port after "-". It then creates a new query that looks exactly like the one it has gotten from
the node before, inputs the updated values in the appropriate places and adds its address to the "pathway" of visited
nodes after a "-". In case its value is bigger than the parameter it just sends the same query it got from the previous
node onward to any neighbor who hasn't recieved it yet. They in turn do the same until there is a node that doesn't have
any neighbors who are not on the list of the ones who were visited already. This "last" node, then, checks the list of
the path of nodes who came before (in order, in the "attach" list). If there are more than one it sends the same message
except for the first word, which becomes OVER_MIN. The nodes that get it, then send the same thing back to the rest of
the nodes in order (since each node can only talk to its neighbor). If there is only one node that comes before this one
then it sends a new message on top of TCP to the "root node", which goes like this: the first line they send is the word
"MIN" and then comes the line that contains the key of the client, and then it sends the key and the value of the
minimum candidate separated by ":" and also the list of the nodes that were visited already also after the ":". It is
followed by the empty line and the communication is over. Then the node that receives this message to its TCP port reads
the first line and knows it's a minimum candidate it then reads the key that it was sent and also the candidate in the
next line. It then checks if the recieved value is smaller than the one already associated with the key it just recieved
and if yes it replaces it with this new value. After all the iterations (with intervals of waiting in between) it sends
the value that is stored with the key of its client to the client as the minimum together with the key associated with
this value. After that comes the empty line
