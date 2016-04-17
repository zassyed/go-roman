Run the follwing command to run the simple web server:
$ go run http.go  

Open browser and goto http://localhost:8000

Replace localhost with the name or IP of your DockerHost.

## Run with docker


Build the image:

    docker build -t myapp .

Test that you can run it:

    docker run -d -p 8000:8000  --name myapp myapp:latest
    curl $(docker-machine ip code):8000
