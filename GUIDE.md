# The Complete Guide: From Zero to Deployed Farewell Website

---

## PHASE 1: Understanding the Big Picture

Before touching anything, let's understand **what we're building and why each piece exists**.

### The Journey of a Request

When your friend Rahul opens `mrdesai.cloud` on his phone, here's what happens behind the scenes:

```
Rahul's Browser
    │
    │  types: mrdesai.cloud
    ▼
DNS (Domain Name System)
    │  "mrdesai.cloud? That's IP 154.x.x.x"
    ▼
Your Hostinger VPS (154.x.x.x)
    │
    │  Port 443 (HTTPS)
    ▼
Nginx (Reverse Proxy)
    │  "This is a web request, let me forward
    │   it to the Spring Boot app on port 8080"
    ▼
Spring Boot App (inside Docker container)
    │  "Show the OTP entry page"
    │
    │  Rahul enters: 482917
    ▼
Spring Boot Controller
    │  "Let me check MySQL for this OTP"
    ▼
MySQL (inside another Docker container)
    │  "OTP 482917 → Rahul's message found!"
    ▼
Spring Boot + Thymeleaf
    │  "Render the farewell page with Rahul's
    │   name and personalized message"
    ▼
Beautiful HTML+CSS Page
    │  Rahul reads the message, clicks "Save as PDF"
    ▼
Browser's print-to-PDF (window.print())
    │  Saves a beautiful PDF keepsake
    ▼
Rahul smiles :)
```

### Why Each Component?

| Component | Why it exists |
|-----------|--------------|
| **Ubuntu 22.04** | The operating system on your VPS. It's the most widely supported Linux for servers. Almost every tutorial, Stack Overflow answer, and Docker guide assumes Ubuntu. |
| **Docker** | Packages your app + MySQL into isolated containers. Without Docker, you'd have to install Java, MySQL, configure them, worry about version conflicts. With Docker, your whole app is a single file (image) that runs identically everywhere. |
| **Docker Compose** | Manages multiple Docker containers together. You need two containers (Spring Boot + MySQL) that need to talk to each other. Compose wires them up with one config file. |
| **Nginx** | A web server that sits in front of your app. It handles HTTPS (SSL certificates), serves as a security boundary, and efficiently forwards requests. You should never expose a Spring Boot app directly to the internet. |
| **Let's Encrypt** | Free SSL certificates so your site is `https://` not `http://`. Browsers flag non-HTTPS sites as "Not Secure". Certbot (a tool) auto-renews these certificates. |
| **GitHub Actions** | Runs your CI/CD pipeline. When you `git push`, GitHub Actions automatically builds your app, creates a Docker image, and deploys it to your VPS. No manual SSH-and-copy needed. |

---

## PHASE 2: Set Up Your Hostinger VPS

### Step 2.1: Install the Operating System

**What you're doing:** Installing Ubuntu on your VPS — this is like installing Windows on a new laptop, but for a server.

**Why Ubuntu 22.04 LTS:** "LTS" means Long Term Support — it gets security updates until 2027. It's the most common server OS, so when you google any problem, you'll find answers.

1. Log into [hpanel.hostinger.com](https://hpanel.hostinger.com)
2. Go to **VPS** → click on your VPS
3. You'll see a setup/installation screen. Choose:
   - **OS:** `Ubuntu 22.04 64bit` (plain OS — **not** any "with panel" option like CyberPanel or cPanel)
   - **Why plain OS and not a panel?** Panels like cPanel add bloat, use RAM, and get in your way when you're doing Docker-based deployments. They're designed for shared hosting workflows (upload files via FTP), not modern CI/CD. Plain OS gives you full control.
4. Set a **root password** — make it strong. Write it down somewhere safe. You'll need this to SSH into your server.
5. Click **Install** / **Setup** and wait 2-3 minutes for it to finish.

### Step 2.2: Note Your VPS IP Address

After installation, your hpanel dashboard will show your **VPS IP address** (something like `154.41.xxx.xxx`).

**Write this down.** You'll need it for:
- Connecting via SSH
- Pointing your domain to it

### Step 2.3: Connect to Your VPS via SSH

**What you're doing:** SSH (Secure Shell) is how you remotely control your server via terminal. Think of it as a remote terminal into your VPS.

**Why from Terminal and not from Hostinger's browser console?** The browser console is slow, can't copy-paste easily, and disconnects. Your Mac's terminal is faster and more reliable.

Open **Terminal** on your Mac Mini and type:

```bash
ssh root@YOUR_VPS_IP
```

Replace `YOUR_VPS_IP` with the IP from Step 2.2.

- First time connecting? It will ask: `Are you sure you want to continue connecting (yes/no)?` — type `yes`
- Enter your root password from Step 2.1

You should now see something like:
```
root@vps-xxxxx:~#
```

**You are now inside your VPS.** Every command you type runs on the server in Amsterdam/London/wherever your VPS is located, not on your Mac.

### Step 2.4: Initial Server Security & Setup

**What you're doing:** Basic security hardening. Your VPS is on the public internet — bots are already trying to break in within minutes of it going live.

Run these commands one by one on your VPS:

```bash
# 1. Update all pre-installed software to latest security patches
#    WHY: The base image may be weeks old with known vulnerabilities
apt update && apt upgrade -y

# 2. Create a non-root user for yourself
#    WHY: Running everything as root is dangerous. One wrong command
#    (like rm -rf /) can destroy everything. A regular user needs
#    to explicitly type 'sudo' for dangerous commands.
adduser mrdesai

# 3. It will ask you to set a password and some optional info
#    Set a strong password. You can skip the Full Name etc by pressing Enter.

# 4. Give your user sudo (admin) privileges
#    WHY: Your user needs to be able to run admin commands when needed,
#    but won't run them by accident
usermod -aG sudo mrdesai

# 5. Set up firewall - allow only SSH, HTTP, and HTTPS
#    WHY: Your VPS has many ports. We only want 3 open:
#    - Port 22 (SSH) - so you can connect
#    - Port 80 (HTTP) - web traffic (also needed for Let's Encrypt)
#    - Port 443 (HTTPS) - secure web traffic
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
#    It will ask "proceed with operation?" → type 'y'

# 6. Verify firewall
ufw status
```

You should see:
```
Status: active

To                         Action      From
--                         ------      ----
OpenSSH                    ALLOW       Anywhere
80/tcp                     ALLOW       Anywhere
443/tcp                    ALLOW       Anywhere
```

### Step 2.5: Test Your New User

**Don't close your current terminal.** Open a **new** Terminal tab/window and test:

```bash
ssh mrdesai@YOUR_VPS_IP
```

If you can log in, great. From now on, **always use this user, not root.**

---

## PHASE 3: Install Docker on Your VPS

### Step 3.1: Install Docker Engine

**What you're doing:** Installing Docker so your VPS can run containers (your app + MySQL).

**Why Docker and not just install Java + MySQL directly?**
- Without Docker: You install Java 21, MySQL 8, configure them, manage their versions, worry about conflicts. When you redeploy, you have to stop the app, copy the new JAR, restart it, hope nothing breaks.
- With Docker: Your entire app is a single image. Redeploy = pull new image, restart container. Rollback = point back to old image. MySQL runs in its own isolated container. Clean, repeatable, predictable.

SSH into your VPS as `mrdesai` and run:

```bash
# 1. Install prerequisites
#    WHY: These packages let apt download over HTTPS and verify signatures
sudo apt install -y ca-certificates curl gnupg

# 2. Add Docker's official GPG key
#    WHY: This lets your system verify that Docker packages are genuinely
#    from Docker Inc. and haven't been tampered with
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 3. Add Docker's repository to your system
#    WHY: Ubuntu's default repositories have an old version of Docker.
#    Docker's own repo always has the latest stable version.
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 4. Install Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 5. Add your user to the docker group
#    WHY: Without this, you'd need 'sudo' before every docker command.
#    Adding yourself to the docker group lets you run docker commands directly.
sudo usermod -aG docker mrdesai

# 6. Log out and back in for group change to take effect
exit
```

SSH back in:
```bash
ssh mrdesai@YOUR_VPS_IP
```

### Step 3.2: Verify Docker Works

```bash
docker --version
# Should show: Docker version 24.x.x or newer

docker compose version
# Should show: Docker Compose version v2.x.x

# Run a test container
docker run hello-world
# Should print "Hello from Docker!" — this confirms everything works
```

---

## PHASE 4: Point Your Domain to Your VPS

### Step 4.1: Configure DNS

**What you're doing:** Telling the entire internet "when someone types mrdesai.cloud, send them to my VPS at IP address 154.x.x.x."

**Why this is a separate step:** A domain name is just a label. It doesn't automatically know where your server is. DNS (Domain Name System) is like the internet's phone book — it maps names to IP addresses.

**Where you do this depends on where you bought the domain:**

If you bought `mrdesai.cloud` from **Hostinger**:
1. Log into hpanel.hostinger.com
2. Go to **Domains** → click `mrdesai.cloud`
3. Go to **DNS / Nameservers** → **DNS Records**
4. Find or create an **A Record**:
   - **Type:** `A`
   - **Name:** `@` (this means the root domain — mrdesai.cloud itself)
   - **Points to:** `YOUR_VPS_IP` (e.g., `154.41.xxx.xxx`)
   - **TTL:** `3600` (or leave default)
5. Create another **A Record** for `www`:
   - **Type:** `A`
   - **Name:** `www`
   - **Points to:** `YOUR_VPS_IP`
   - **TTL:** `3600`

**Why two records?** The `@` record handles `mrdesai.cloud`. The `www` record handles `www.mrdesai.cloud`. Some people type `www.`, some don't. We want both to work.

If you bought the domain from a **different registrar** (GoDaddy, Namecheap, etc.), the process is similar — find DNS settings and add the same two A records.

### Step 4.2: Verify DNS (Wait 5-30 minutes)

**Why the wait?** DNS changes propagate across the internet. It takes time for DNS servers worldwide to learn about your new record.

From your **Mac's terminal** (not the VPS):

```bash
# Check if DNS has propagated
dig mrdesai.cloud +short
# Should eventually show your VPS IP address

# Or use:
nslookup mrdesai.cloud
```

If it shows your VPS IP, DNS is working. If not, wait and try again.

---

## PHASE 5: Set Up Nginx + HTTPS on Your VPS

### Step 5.1: Install Nginx

**What you're doing:** Installing Nginx, a web server that will sit in front of your Spring Boot app.

**Why not let Spring Boot handle requests directly?**
- Nginx handles HTTPS termination (SSL certificates) — Spring Boot shouldn't deal with this
- Nginx is battle-hardened for direct internet exposure; Spring Boot's embedded Tomcat is not designed for that
- Nginx can serve static files faster
- Nginx acts as a shield — if your Java app crashes, Nginx can show a friendly error page instead of a connection refused

SSH into your VPS:

```bash
# Install Nginx
sudo apt install -y nginx

# Verify it's running
sudo systemctl status nginx
```

Now open a browser on your Mac and go to `http://mrdesai.cloud` (or `http://YOUR_VPS_IP` if DNS hasn't propagated yet). You should see the **"Welcome to nginx!"** default page. This confirms Nginx is working and your firewall/DNS are correct.

### Step 5.2: Configure Nginx as a Reverse Proxy

**What you're doing:** Telling Nginx: "When someone visits mrdesai.cloud, forward the request to the Spring Boot app running on port 8080."

**What is a reverse proxy?** Normally, a "proxy" hides the client's identity from the server (like a VPN). A "reverse proxy" hides the server's identity from the client. Your users think they're talking to Nginx, but Nginx is secretly forwarding everything to Spring Boot. This gives Nginx control over security, caching, and load balancing.

```bash
# Create a new Nginx config file for your site
sudo nano /etc/nginx/sites-available/mrdesai.cloud
```

**About nano:** This is a simple text editor in the terminal. You type, use arrow keys to move around. When done: `Ctrl+O` to save, `Enter` to confirm, `Ctrl+X` to exit.

Paste this content:

```nginx
server {
    listen 80;
    server_name mrdesai.cloud www.mrdesai.cloud;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

**What each line means:**
- `listen 80` — Listen on port 80 (HTTP)
- `server_name` — Only respond to requests for mrdesai.cloud (not random IP scanners)
- `proxy_pass http://localhost:8080` — Forward to Spring Boot on port 8080
- `proxy_set_header` lines — Pass along the real visitor's IP and other info to Spring Boot (otherwise Spring Boot would think every request comes from localhost)

Now activate this config:

```bash
# Create a symbolic link to enable the site
#    WHY: Nginx uses two folders: sites-available (all configs) and
#    sites-enabled (active configs). The symlink activates your config.
sudo ln -s /etc/nginx/sites-available/mrdesai.cloud /etc/nginx/sites-enabled/

# Remove the default site (we don't need "Welcome to nginx!" anymore)
sudo rm /etc/nginx/sites-enabled/default

# Test your config for syntax errors
sudo nginx -t
# Should say: "syntax is ok" and "test is successful"

# Reload Nginx to apply changes
sudo systemctl reload nginx
```

If you visit `http://mrdesai.cloud` now, you'll see a **502 Bad Gateway** error. That's expected and actually good — it means Nginx is working and trying to forward to port 8080, but nothing is running there yet.

### Step 5.3: Set Up HTTPS with Let's Encrypt

**What you're doing:** Getting a free SSL certificate so your site is `https://` with the padlock icon.

**Why HTTPS matters:**
- Browsers show "Not Secure" for HTTP sites — your friends might not trust the OTP form
- OTPs typed into an HTTP form are sent in plain text — anyone on the same WiFi could read them
- It's free and takes 2 minutes. No reason not to.

```bash
# Install Certbot (the tool that gets Let's Encrypt certificates)
sudo apt install -y certbot python3-certbot-nginx

# Get the certificate — Certbot will automatically modify your Nginx config
#    WHY the --nginx flag: Certbot can automatically configure Nginx to use
#    the new certificate. Without it, you'd have to manually edit config files.
sudo certbot --nginx -d mrdesai.cloud -d www.mrdesai.cloud
```

Certbot will ask you:
- **Email address:** Enter yours (for renewal warnings)
- **Agree to terms:** `Y`
- **Share email with EFF:** Your choice, `N` is fine

Certbot automatically:
- Gets certificates from Let's Encrypt
- Modifies your Nginx config to use them
- Sets up HTTP→HTTPS redirect (anyone visiting `http://` gets redirected to `https://`)

### Step 5.4: Verify Auto-Renewal

Certificates expire every 90 days. Certbot sets up auto-renewal, but let's verify:

```bash
# Test that auto-renewal works
sudo certbot renew --dry-run
# Should say: "Congratulations, all simulated renewals succeeded"
```

**Why `--dry-run`?** It simulates the renewal without actually doing it. Your certificate is brand new — no need to renew yet. This just confirms the auto-renewal mechanism works.

---

## PHASE 6: Create the Spring Boot Project — DONE

You've already created the project in IntelliJ. Here's what you have:

- **Spring Boot 4.2.0** with Java 21
- **Dependencies:** Spring Data JPA, Thymeleaf, Validation, WebMVC, MySQL Driver, DevTools
- **Package:** `cloud.mrdesai.goodbye`
- **Entry point:** `GoodbyeApplication.java`

Everything is correctly set up. The `pom.xml` has all the dependencies we need.

---

## STATUS: Phases 1-6 Complete

After completing the above, you should have:
- [x] VPS running Ubuntu 22.04 with a non-root user
- [x] Docker installed on VPS
- [x] Domain pointing to VPS
- [x] Nginx running as reverse proxy with HTTPS
- [x] Spring Boot 4.2.0 project created locally in IntelliJ (with JPA, Thymeleaf, Validation, WebMVC, MySQL, DevTools)

---

## PHASE 7: The Actual Code — Entities, Controllers, Templates, CSS

This is the heart of your app. By the end of this phase you'll have a fully working farewell website running locally.

### Understanding the Project Structure

Here's what you currently have, and what we're about to create:

```
goodbye/
├── mvnw, mvnw.cmd                                 ← Maven wrapper (already exists)
├── pom.xml                                        ← Maven dependencies (already exists)
├── src/
│   ├── main/
│   │   ├── java/cloud/mrdesai/goodbye/
│   │   │   ├── GoodbyeApplication.java            ← Spring Boot entry point (already exists)
│   │   │   ├── model/
│   │   │   │   └── FarewellMessage.java            ← CREATE THIS — the database entity
│   │   │   ├── repository/
│   │   │   │   └── FarewellMessageRepository.java  ← CREATE THIS — talks to MySQL
│   │   │   └── controller/
│   │   │       └── FarewellController.java         ← CREATE THIS — handles web requests
│   │   └── resources/
│   │       ├── application.properties              ← UPDATE THIS — add DB config
│   │       ├── application-dev.properties          ← CREATE THIS — local dev config
│   │       ├── templates/
│   │       │   ├── otp.html                        ← CREATE THIS — OTP entry page
│   │       │   ├── farewell.html                   ← CREATE THIS — farewell message page
│   │       │   └── error.html                      ← CREATE THIS — error page
│   │       └── static/
│   │           └── css/
│   │               └── style.css                   ← CREATE THIS — all styling
│   └── test/
│       └── java/cloud/mrdesai/goodbye/
│           └── GoodbyeApplicationTests.java        ← (already exists, leave it)
├── Dockerfile                                      ← CREATE in Phase 8
├── docker-compose.yml                              ← CREATE in Phase 8
└── .github/workflows/deploy.yml                    ← CREATE in Phase 10
```

**Why this structure?** Spring Boot follows a convention: `model/` for database objects, `repository/` for database access, `controller/` for HTTP handling. This isn't arbitrary — Spring automatically scans these packages and wires everything together. The packages must be under `cloud.mrdesai.goodbye` (or sub-packages of it) because that's where `@SpringBootApplication` lives — Spring only scans that package and below.

### Step 7.1: Application Properties

**What you're doing:** Configuring how Spring Boot connects to MySQL and behaves in different environments.

**Why two property files?** In development (your Mac), MySQL runs on `localhost:3306`. In production (the VPS), MySQL runs inside a Docker container named `mysql`. Using Spring profiles lets you switch between configs without changing code.

Your `application.properties` currently only has `spring.application.name=goodbye`. Replace it with the full production config.

Open `src/main/resources/application.properties` and replace its contents with:

```properties
# This is the PRODUCTION config (used by default when no profile is active)
# Spring will use these settings when your app runs inside Docker on the VPS

# --- Database Connection ---
# "mysql" is the hostname of the MySQL container in Docker Compose
# Docker Compose creates a network where containers can find each other by name
spring.datasource.url=jdbc:mysql://mysql:3306/goodbye_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:SwastikDesai@99}

# --- JPA / Hibernate ---
# "update" means: on startup, compare entities to the database schema and
# ALTER tables to match. This is fine for a small project like ours.
# (In a large production app you'd use Flyway or Liquibase for migrations)
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# --- Server ---
server.port=8080
```

**What are `${DB_USERNAME:root}` and `${DB_PASSWORD:changeme}`?**
This is Spring's property placeholder syntax. It means: "Use the environment variable `DB_USERNAME` if it exists, otherwise fall back to `root`." In production, Docker Compose will set these environment variables. The fallback values are for convenience, not security — we'll set real values in the `.env` file.

Now create `src/main/resources/application-dev.properties`:

```properties
# This is the DEVELOPMENT config (used when you run with -Dspring.profiles.active=dev)
# Connects to MySQL running directly on your Mac (via Docker or Homebrew)

spring.datasource.url=jdbc:mysql://localhost:3306/goodbye_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=devpassword

spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

server.port=8080
```

**How to run a local MySQL for development:**

```bash
# On your Mac, run a MySQL container for development
docker run -d --name goodbye-mysql-dev -e MYSQL_ROOT_PASSWORD=devpassword -e MYSQL_DATABASE=goodbye_db -p 3306:3306 mysql:8.0

# This runs MySQL in the background on port 3306
# To stop it later: docker stop goodbye-mysql-dev
# To start it again: docker start goodbye-mysql-dev
```

### Step 7.2: The Entity — FarewellMessage.java

**What you're doing:** Defining the shape of your data. This Java class maps directly to a MySQL table.

**What is an Entity?** In JPA (Java Persistence API), an `@Entity` class is a Java object that represents a row in a database table. Each field becomes a column. When Spring Boot starts, Hibernate (the JPA implementation) automatically creates or updates the MySQL table to match this class.

Create `src/main/java/cloud/mrdesai/goodbye/model/FarewellMessage.java`:

```java
package cloud.mrdesai.goodbye.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "farewell_messages")
public class FarewellMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The 6-digit OTP code — unique per friend
    @Column(nullable = false, unique = true, length = 6)
    private String otp;

    // The friend's name (displayed on the farewell page)
    @Column(nullable = false)
    private String recipientName;

    // The personalized farewell message (can be long)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    // Track whether the message has been opened
    @Column(nullable = false)
    private boolean opened = false;

    // When they first opened it (null until opened)
    private LocalDateTime openedAt;

    // --- Constructors ---

    public FarewellMessage() {
        // JPA requires a no-arg constructor
    }

    public FarewellMessage(String otp, String recipientName, String message) {
        this.otp = otp;
        this.recipientName = recipientName;
        this.message = message;
    }

    // --- Getters and Setters ---
    // (IntelliJ shortcut: right-click → Generate → Getters and Setters → Select All)

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isOpened() { return opened; }
    public void setOpened(boolean opened) { this.opened = opened; }

    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
}
```

**Why these fields?**
- `otp` — The 6-digit code you'll text to each friend. Marked `unique` so no two friends can have the same OTP.
- `recipientName` — Shown on the farewell page ("Dear Rahul, ...").
- `message` — The actual farewell text. `TEXT` column type because some messages might be paragraphs long.
- `opened` / `openedAt` — So you can see who has read their message. This is optional but fun — you'll know who opened yours!

### Step 7.3: The Repository — FarewellMessageRepository.java

**What you're doing:** Creating the database access layer. This interface lets you query MySQL using Java method names instead of raw SQL.

**What is a Repository?** Spring Data JPA is almost magical here. You write an interface (not a class!) with method names that follow a naming convention, and Spring automatically generates the SQL queries. `findByOtp(String otp)` becomes `SELECT * FROM farewell_messages WHERE otp = ?` — no SQL written by you.

Create `src/main/java/cloud/mrdesai/goodbye/repository/FarewellMessageRepository.java`:

```java
package cloud.mrdesai.goodbye.repository;

import cloud.mrdesai.goodbye.model.FarewellMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FarewellMessageRepository extends JpaRepository<FarewellMessage, Long> {

    // Spring Data JPA reads this method name and generates:
    // SELECT * FROM farewell_messages WHERE otp = ?
    //
    // Optional<> means: the result might be empty (if the OTP doesn't exist)
    // This forces us to handle the "not found" case explicitly — no NullPointerExceptions
    Optional<FarewellMessage> findByOtp(String otp);
}
```

**That's it. No implementation class needed.** Spring generates one at startup. This is one of Spring Boot's biggest productivity wins.

### Step 7.4: The Controller — FarewellController.java

**What you're doing:** Defining what happens when someone visits your website. The controller handles HTTP requests and decides which HTML page to show.

**What is a Controller?** In the MVC (Model-View-Controller) pattern:
- **Model** = your data (FarewellMessage)
- **View** = the HTML pages (Thymeleaf templates)
- **Controller** = the traffic cop that connects them

When Rahul visits `mrdesai.cloud`, the controller shows the OTP form. When he submits his OTP, the controller looks it up in MySQL and shows his personalized farewell page.

Create `src/main/java/cloud/mrdesai/goodbye/controller/FarewellController.java`:

```java
package cloud.mrdesai.goodbye.controller;

import cloud.mrdesai.goodbye.model.FarewellMessage;
import cloud.mrdesai.goodbye.repository.FarewellMessageRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Controller
public class FarewellController {

    private final FarewellMessageRepository repository;

    // Constructor injection — Spring automatically passes the repository instance
    // WHY constructor injection over @Autowired on the field?
    // It makes dependencies explicit and the class easier to test
    public FarewellController(FarewellMessageRepository repository) {
        this.repository = repository;
    }

    // GET / → Show the OTP entry form
    @GetMapping("/")
    public String showOtpForm() {
        // "otp" refers to the template: src/main/resources/templates/otp.html
        return "otp";
    }

    // POST /enter → Handle OTP submission
    @PostMapping("/enter")
    public String handleOtp(@RequestParam("otp") String otp, Model model) {
        // Clean the input: remove spaces, trim
        String cleanOtp = otp.trim().replaceAll("\\s+", "");

        // Validate: must be exactly 6 digits
        if (!cleanOtp.matches("\\d{6}")) {
            model.addAttribute("error", "Please enter a valid 6-digit code.");
            return "otp";
        }

        // Look up the OTP in the database
        Optional<FarewellMessage> result = repository.findByOtp(cleanOtp);

        if (result.isEmpty()) {
            model.addAttribute("error", "Hmm, that code doesn't seem right. Double-check and try again!");
            return "otp";
        }

        // OTP found! Redirect to the farewell page
        // WHY redirect instead of rendering directly?
        // If we render directly, refreshing the page would re-submit the form
        // (the browser would ask "Resubmit form data?"). Redirect avoids this.
        return "redirect:/farewell/" + cleanOtp;
    }

    // GET /farewell/{otp} → Show the personalized farewell message
    @GetMapping("/farewell/{otp}")
    public String showFarewell(@PathVariable("otp") String otp, Model model) {
        Optional<FarewellMessage> result = repository.findByOtp(otp);

        if (result.isEmpty()) {
            return "redirect:/";
        }

        FarewellMessage msg = result.get();

        // Mark as opened (only the first time)
        if (!msg.isOpened()) {
            msg.setOpened(true);
            msg.setOpenedAt(LocalDateTime.now());
            repository.save(msg);
        }

        // Pass the message data to the template
        model.addAttribute("msg", msg);
        return "farewell";
    }
}
```

**Flow summary:**
1. `GET /` → shows `otp.html` (the OTP input form)
2. User submits form → `POST /enter` validates the OTP
3. If valid → `redirect:/farewell/482917` → `GET /farewell/482917` → shows `farewell.html`
4. If invalid → shows `otp.html` again with an error message

### Step 7.5: The Templates — Thymeleaf HTML

**What is Thymeleaf?** A Java template engine. It lets you write HTML files with special attributes like `th:text="${msg.recipientName}"` that get replaced with real data at runtime. The HTML files are valid on their own (you can open them in a browser), but when Spring Boot serves them, the dynamic parts get filled in.

#### otp.html — The OTP Entry Page

Create `src/main/resources/templates/otp.html`:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>A Message For You</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body class="otp-page">
    <div class="otp-container">
        <h1>You've received a message</h1>
        <p class="subtitle">Enter the 6-digit code you were given to unlock it.</p>

        <!-- Show error message if OTP was invalid -->
        <div th:if="${error}" class="error-message">
            <p th:text="${error}"></p>
        </div>

        <form action="/enter" method="post">
            <div class="otp-input-group">
                <input type="text"
                       name="otp"
                       maxlength="6"
                       pattern="\d{6}"
                       placeholder="000000"
                       required
                       autofocus
                       autocomplete="off"
                       inputmode="numeric">
            </div>
            <button type="submit" class="btn-unlock">Unlock Message</button>
        </form>
    </div>
</body>
</html>
```

**Template details:**
- `th:href="@{/css/style.css}"` — Thymeleaf resolves this to the correct path at runtime. The `@{...}` syntax handles context paths automatically.
- `th:if="${error}"` — Only renders this `<div>` if the controller added an `error` attribute to the model.
- `th:text="${error}"` — Replaces the tag's content with the error message. This is XSS-safe — Thymeleaf escapes HTML by default.
- `inputmode="numeric"` — On mobile phones, this shows the number keypad instead of the full keyboard.

#### farewell.html — The Farewell Message Page

Create `src/main/resources/templates/farewell.html`:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title th:text="'For ' + ${msg.recipientName}">For You</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body class="farewell-page">
    <div class="farewell-container">
        <div class="letter">
            <p class="greeting" th:text="'Dear ' + ${msg.recipientName} + ','">Dear Friend,</p>

            <!-- th:text escapes HTML (XSS-safe), and white-space: pre-line
                 in the CSS handles line breaks visually — no <br> tags needed -->
            <div class="message-body" th:text="${msg.message}">
                Your message will appear here.
            </div>

            <p class="sign-off">With gratitude,</p>
            <p class="signature">Swapnil</p>
        </div>

        <!-- Save as PDF button (hidden when printing) -->
        <div class="actions no-print">
            <button onclick="window.print()" class="btn-save">Save as PDF</button>
            <p class="save-hint">Tip: In the print dialog, choose "Save as PDF" as the destination.</p>
        </div>
    </div>
</body>
</html>
```

**Why `th:text` and not `th:utext`?** There are two Thymeleaf text attributes:
- `th:text` — Escapes HTML (safe, prevents XSS)
- `th:utext` — Renders raw HTML (unsafe if input comes from users)

We use `th:text` because it's safer, and we handle line breaks via `white-space: pre-line` in the CSS instead of converting `\n` to `<br>` tags. Simple and secure.

#### error.html — Custom Error Page

Create `src/main/resources/templates/error.html`:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Oops</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body class="otp-page">
    <div class="otp-container">
        <h1>Something went wrong</h1>
        <p class="subtitle">The page you're looking for doesn't exist.</p>
        <a href="/" class="btn-unlock" style="text-decoration: none; display: inline-block; margin-top: 1.5rem;">Go Back</a>
    </div>
</body>
</html>
```

**Why a custom error page?** Without this, Spring Boot shows a "Whitelabel Error Page" with a stack trace. That looks unprofessional and leaks technical details. By creating `error.html` in the templates folder, Spring Boot automatically uses it for all error pages.

### Step 7.6: The CSS — Making It Beautiful

**What you're doing:** Styling the OTP page and farewell page. We want this to feel special — like opening a handwritten letter, not a corporate email.

Create `src/main/resources/static/css/style.css`:

```css
/* ============================================
   FAREWELL WEBSITE — STYLES
   ============================================ */

/* --- Reset & Base --- */
*, *::before, *::after {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: 'Georgia', 'Times New Roman', serif;
    min-height: 100vh;
    display: flex;
    justify-content: center;
    align-items: center;
}

/* --- OTP Page --- */
.otp-page {
    background: linear-gradient(135deg, #0f0c29, #302b63, #24243e);
    color: #e0e0e0;
}

.otp-container {
    text-align: center;
    padding: 3rem 2rem;
    max-width: 420px;
    width: 90%;
}

.otp-container h1 {
    font-size: 1.8rem;
    font-weight: 400;
    color: #ffffff;
    margin-bottom: 0.5rem;
    letter-spacing: 0.5px;
}

.subtitle {
    font-size: 1rem;
    color: #a0a0b0;
    margin-bottom: 2rem;
    line-height: 1.5;
}

.otp-input-group input {
    width: 100%;
    padding: 1rem;
    font-size: 2rem;
    text-align: center;
    letter-spacing: 1rem;
    font-family: 'Courier New', monospace;
    background: rgba(255, 255, 255, 0.08);
    border: 2px solid rgba(255, 255, 255, 0.15);
    border-radius: 12px;
    color: #ffffff;
    outline: none;
    transition: border-color 0.3s;
}

.otp-input-group input:focus {
    border-color: rgba(255, 255, 255, 0.5);
}

.otp-input-group input::placeholder {
    color: rgba(255, 255, 255, 0.2);
    letter-spacing: 1rem;
}

.btn-unlock {
    margin-top: 1.5rem;
    padding: 0.9rem 2.5rem;
    font-size: 1rem;
    font-family: 'Georgia', serif;
    background: rgba(255, 255, 255, 0.12);
    color: #ffffff;
    border: 1px solid rgba(255, 255, 255, 0.25);
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.3s;
    letter-spacing: 1px;
}

.btn-unlock:hover {
    background: rgba(255, 255, 255, 0.2);
    border-color: rgba(255, 255, 255, 0.4);
}

.error-message {
    background: rgba(255, 80, 80, 0.15);
    border: 1px solid rgba(255, 80, 80, 0.3);
    border-radius: 8px;
    padding: 0.8rem 1.2rem;
    margin-bottom: 1.5rem;
    color: #ff9090;
    font-size: 0.9rem;
}

/* --- Farewell Page --- */
.farewell-page {
    background: #faf6f0;
    color: #2c2c2c;
    padding: 2rem 1rem;
}

.farewell-container {
    max-width: 650px;
    width: 100%;
}

.letter {
    background: #ffffff;
    border: 1px solid #e8e0d4;
    border-radius: 4px;
    padding: 3rem 2.5rem;
    box-shadow: 0 2px 20px rgba(0, 0, 0, 0.06);
    position: relative;
}

/* Subtle decorative top border */
.letter::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg, #c9a96e, #d4b87a, #c9a96e);
}

.greeting {
    font-size: 1.5rem;
    font-style: italic;
    color: #4a4a4a;
    margin-bottom: 1.8rem;
}

.message-body {
    font-size: 1.1rem;
    line-height: 1.9;
    color: #3a3a3a;
    margin-bottom: 2rem;
    white-space: pre-line;
}

.sign-off {
    font-size: 1.1rem;
    color: #5a5a5a;
    margin-top: 2rem;
    font-style: italic;
}

.signature {
    font-size: 1.3rem;
    color: #2c2c2c;
    margin-top: 0.3rem;
    font-weight: 600;
}

/* --- Save as PDF Button --- */
.actions {
    text-align: center;
    margin-top: 2rem;
}

.btn-save {
    padding: 0.8rem 2rem;
    font-size: 1rem;
    font-family: 'Georgia', serif;
    background: #2c2c2c;
    color: #ffffff;
    border: none;
    border-radius: 6px;
    cursor: pointer;
    transition: background 0.3s;
}

.btn-save:hover {
    background: #444444;
}

.save-hint {
    margin-top: 0.8rem;
    font-size: 0.8rem;
    color: #999;
    font-style: italic;
}

/* --- Print Styles (for Save as PDF) --- */
@media print {
    /* Hide the button when printing */
    .no-print {
        display: none !important;
    }

    body {
        background: white;
    }

    .farewell-page {
        padding: 0;
    }

    .letter {
        box-shadow: none;
        border: none;
        padding: 2rem 1rem;
    }

    .letter::before {
        display: none;
    }
}

/* --- Mobile Responsiveness --- */
@media (max-width: 480px) {
    .otp-container h1 {
        font-size: 1.4rem;
    }

    .otp-input-group input {
        font-size: 1.5rem;
        letter-spacing: 0.6rem;
        padding: 0.8rem;
    }

    .letter {
        padding: 2rem 1.5rem;
    }

    .greeting {
        font-size: 1.3rem;
    }

    .message-body {
        font-size: 1rem;
    }
}
```

**Key CSS decisions:**
- **OTP page:** Dark, mysterious gradient — creates a sense of intrigue. "What's behind this code?"
- **Farewell page:** Warm, paper-like cream background with a white "letter" card. Feels personal and intimate, like a handwritten note.
- **`white-space: pre-line`** on `.message-body` — This preserves line breaks from your messages without needing `<br>` tags. If your message in the database has `\n` newlines, they'll display correctly.
- **`@media print`** — When someone clicks "Save as PDF," the browser uses these styles. We hide the button, remove shadows, and clean up the layout so the PDF looks like a clean letter.

### Step 7.7: Test Locally

**Before moving to Docker and deployment, make sure everything works on your Mac.**

1. Make sure your dev MySQL is running (from Step 7.1)
2. In IntelliJ, open the **Run Configuration**:
   - Click the dropdown next to the green Run button → **Edit Configurations**
   - Under **Spring Boot** → **GoodbyeApplication**
   - In **Active profiles**, type: `dev`
   - This tells Spring Boot to use `application-dev.properties`
3. Click the green **Run** button
4. Open your browser and go to `http://localhost:8080`
5. You should see the OTP entry page

**But there are no messages in the database yet!** Let's insert a test one. Open a terminal:

```bash
# Connect to your local MySQL container
docker exec -it goodbye-mysql-dev mysql -u root -pdevpassword goodbye_db

# Inside MySQL, insert a test message:
INSERT INTO farewell_messages (otp, recipient_name, message, opened)
VALUES ('123456', 'Test Friend', 'This is a test farewell message.\n\nYou were an amazing colleague and I will miss our coffee breaks.\n\nKeep being awesome!', false);

# Exit MySQL
exit
```

Now go to `http://localhost:8080`, enter `123456`, and you should see the farewell page with the styled message.

---

## PHASE 8: Docker Setup — Containerizing Your App

### Why Docker?

You could SSH into the VPS, install Java 21, install MySQL 8, copy your JAR file, configure everything manually... and do that every time you deploy. Docker eliminates all of that. Your entire app becomes a single image that runs the same everywhere.

### Step 8.1: The Dockerfile

**What you're doing:** Writing instructions for Docker to build your application into an image.

**What is a multi-stage build?** Instead of one giant image with Maven, source code, AND the compiled app, we use two stages:
1. **Build stage:** Uses a Maven image to compile your code into a JAR
2. **Run stage:** Uses a slim JRE image with just the compiled JAR

The final image is ~300MB instead of ~800MB, and doesn't contain your source code (better security).

Create `Dockerfile` in the project root (same level as `pom.xml`):

```dockerfile
# ---- Stage 1: Build ----
# Use a Maven image that includes JDK 21 to compile the project
FROM maven:3.9-eclipse-temurin-21 AS build

# Set the working directory inside the container
WORKDIR /app

# Copy ONLY the pom.xml first, then download dependencies
# WHY? Docker caches each layer. If pom.xml hasn't changed, Docker
# reuses the cached dependencies instead of re-downloading them.
# This makes subsequent builds MUCH faster (seconds vs minutes).
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the source code and build
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Stage 2: Run ----
# Use a slim JRE image (no compiler, no Maven — just what's needed to run)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy ONLY the compiled JAR from the build stage
# The *.jar glob handles the version number in the filename
COPY --from=build /app/target/*.jar app.jar

# Document which port the app uses (doesn't actually publish it)
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 8.2: The .dockerignore File

**What you're doing:** Telling Docker which files to skip when copying into the build context.

**Why?** Without this, Docker copies everything — `.git/`, `.idea/`, `target/`, etc. These slow down the build and bloat the context. Think of it like `.gitignore` but for Docker.

Create `.dockerignore` in the project root:

```
.git
.gitignore
.idea
*.md
memory/
target
docker-compose.yml
.env
```

### Step 8.3: Docker Compose

**What you're doing:** Defining how your Spring Boot app and MySQL should run together.

**Why Docker Compose?** You need two containers (app + MySQL) that must:
- Start in the right order (MySQL before the app)
- Share a network (the app needs to reach MySQL by hostname)
- Persist data (MySQL data survives container restarts)
- Use environment variables for configuration

Docker Compose handles all of this in one YAML file.

Create `docker-compose.yml` in the project root:

```yaml
services:
  # --- MySQL Database ---
  mysql:
    image: mysql:8.0
    container_name: goodbye-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: goodbye_db
    volumes:
      # Named volume — MySQL data persists even if the container is destroyed
      # Without this, every container restart = empty database = all messages gone
      - mysql_data:/var/lib/mysql
    ports:
      # Expose MySQL on port 3307 on the host (not 3306, in case the host
      # already has MySQL running). You'll use this for direct DB access.
      - "3307:3306"
    healthcheck:
      # Docker will check if MySQL is actually ready to accept connections
      # WHY? MySQL takes a few seconds to initialize. Without a healthcheck,
      # the Spring Boot app might start before MySQL is ready and crash.
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  # --- Spring Boot Application ---
  app:
    image: ghcr.io/BITCODER10/goodbye:latest
    container_name: goodbye-app
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      DB_USERNAME: root
      DB_PASSWORD: ${DB_PASSWORD}
    depends_on:
      mysql:
        # Wait for MySQL to be healthy, not just "started"
        condition: service_healthy

volumes:
  mysql_data:
```

**Important:** Replace `YOUR_GITHUB_USERNAME` with your actual GitHub username (all lowercase). This is where your Docker image will be stored (GitHub Container Registry). We'll set this up in Phase 10.

**What's `ghcr.io`?** GitHub Container Registry — a free Docker image registry that comes with every GitHub account. Instead of Docker Hub, we store our images on GitHub alongside our code.

### Step 8.4: The .env File

**What you're doing:** Storing secrets (passwords) separately from your code.

**Why a .env file?** You never want passwords in your `docker-compose.yml` (which gets committed to GitHub). The `.env` file stays on your server and is read by Docker Compose automatically.

Create `.env` in the project root (for local testing only — this file will NOT be committed):


**On the VPS (production)**, you'll create a different `.env` with a strong password. We'll do that in Phase 11.

### Step 8.5: Test Docker Compose Locally

```bash
# From the project root (where docker-compose.yml is):

# First, build the Docker image locally
docker build -t ghcr.io/bitcoder10/goodbye:latest .

# Start everything
docker compose up -d

# Check both containers are running
docker compose ps

# Check the app logs
docker compose logs -f app
```

You should see Spring Boot starting up and connecting to MySQL. Visit `http://localhost:8080` to verify.

```bash
# To stop everything:
docker compose down

# To stop AND delete the database volume (fresh start):
docker compose down -v
```

---

## PHASE 9: GitHub Repository Setup

### Step 9.1: Create .gitignore

**What you're doing:** Telling Git which files should never be committed.

Create `.gitignore` in the project root:

```gitignore
# --- Java / Maven ---
target/
*.class
*.jar
*.war

# --- IntelliJ IDEA ---
.idea/
*.iml
out/

# --- Environment & Secrets ---
.env

# --- Claude Code memory (not part of the app) ---
memory/
MEMORY.md

# --- OS ---
.DS_Store
Thumbs.db

# --- Docker (local volumes) ---
mysql_data/
```

**Why exclude `.env`?** It contains your database password. If you commit it and push to GitHub, anyone who can see your repo can see your password. Even if you delete it later, it stays in Git history forever.

### Step 9.2: Initialize Git & Create GitHub Repository

```bash
# Navigate to your project
cd /Users/mrdesai/Desktop/goodbye

# Initialize Git
git init

# Add all files (respecting .gitignore)
git add .

# Create the first commit
git commit -m "Initial commit: Spring Boot farewell website"
```

Now create a repository on GitHub:

1. Go to [github.com/new](https://github.com/new)
2. **Repository name:** `goodbye`
3. **Visibility:** `Private` (your farewell messages are personal!)
4. **Do NOT** initialize with README, .gitignore, or license (you already have these locally)
5. Click **Create repository**

GitHub will show you commands. Run:

```bash
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/goodbye.git
git branch -M main
git push -u origin main
```

Your code is now on GitHub. Refresh the GitHub page to verify.

---

## PHASE 10: GitHub Actions CI/CD Pipeline

This is the phase that automates everything. After this, pushing code to GitHub will automatically build and deploy your app to the VPS.

### Understanding the CI/CD Flow

```
You push code to GitHub
        │
        ▼
GitHub Actions triggers
        │
        ├── 1. Checks out your code
        ├── 2. Builds a Docker image (using your Dockerfile)
        ├── 3. Pushes the image to GitHub Container Registry (ghcr.io)
        ├── 4. SSHes into your VPS
        ├── 5. Pulls the new image on the VPS
        └── 6. Restarts the containers with the new image
```

### Step 10.1: Set Up SSH Key for Deployment

**What you're doing:** Creating a key pair that lets GitHub Actions SSH into your VPS without a password.

**Why SSH keys over passwords?** Passwords in CI/CD are fragile and less secure. SSH key authentication is the standard for automated deployments. The private key stays in GitHub Secrets (encrypted), and the public key goes on your VPS.

On your **Mac**:

```bash
# Generate a dedicated deploy key (NOT your personal SSH key)
# WHY a separate key? If this key is ever compromised, you revoke it
# without losing access to your own SSH sessions.
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/goodbye_deploy_key
# When asked for a passphrase, press Enter (no passphrase)
# WHY no passphrase? GitHub Actions can't type a passphrase interactively.
```

This creates two files:
- `~/.ssh/goodbye_deploy_key` — the **private key** (goes to GitHub Secrets — NEVER share this)
- `~/.ssh/goodbye_deploy_key.pub` — the **public key** (goes on the VPS)

Now put the **public key** on your VPS:

```bash
# Copy the public key to your VPS
# This adds it to the authorized_keys file for the mrdesai user
ssh-copy-id -i ~/.ssh/goodbye_deploy_key.pub mrdesai@YOUR_VPS_IP
```

**Or manually**, if `ssh-copy-id` doesn't work:

```bash
# Print the public key
cat ~/.ssh/goodbye_deploy_key.pub

# SSH into your VPS
ssh mrdesai@YOUR_VPS_IP

# Add the key
mkdir -p ~/.ssh
echo "THE_PUBLIC_KEY_YOU_COPIED" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys

exit
```

Verify it works:

```bash
ssh -i ~/.ssh/goodbye_deploy_key mrdesai@YOUR_VPS_IP
# Should log in without asking for a password
```

### Step 10.2: Add GitHub Secrets

**What you're doing:** Storing sensitive values (SSH key, VPS IP) in GitHub's encrypted secrets vault. GitHub Actions can read these, but no one — not even you — can see them again after they're saved.

Go to your GitHub repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

Add these secrets:

| Secret Name | Value |
|-------------|-------|
| `VPS_HOST` | Your VPS IP address (e.g., `154.41.xxx.xxx`) |
| `VPS_USERNAME` | `mrdesai` |
| `VPS_SSH_KEY` | The **private key** content (see below) |

For `VPS_SSH_KEY`, copy the entire private key:

```bash
cat ~/.ssh/goodbye_deploy_key
```

Copy **everything** from `-----BEGIN OPENSSH PRIVATE KEY-----` to `-----END OPENSSH PRIVATE KEY-----` (inclusive) and paste it as the secret value.

### Step 10.3: Enable GitHub Container Registry

**What you're doing:** Allowing GitHub Actions to push Docker images to your GitHub Container Registry.

You need a **Personal Access Token (PAT)** with package permissions:

1. Go to [github.com/settings/tokens](https://github.com/settings/tokens) → **Generate new token (classic)**
2. **Note:** `goodbye-ghcr`
3. **Expiration:** 90 days (or longer)
4. **Scopes:** Check `write:packages` and `read:packages`
5. Click **Generate token**
6. **Copy the token immediately** — you won't see it again

Add it as another GitHub Secret:

| Secret Name | Value |
|-------------|-------|
| `GHCR_TOKEN` | The token you just copied |

### Step 10.4: The GitHub Actions Workflow

**What you're doing:** Writing the CI/CD pipeline that automatically builds and deploys your app.

Create the directory structure and file:

```bash
mkdir -p .github/workflows
```

Create `.github/workflows/deploy.yml`:

```yaml
name: Build and Deploy

# Trigger: run this pipeline whenever code is pushed to the main branch
on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    steps:
      # 1. Check out the code
      - name: Checkout code
        uses: actions/checkout@v4

      # 2. Log in to GitHub Container Registry
      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GHCR_TOKEN }}

      # 3. Build and push the Docker image
      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ghcr.io/${{ github.repository_owner }}/goodbye:latest
            ghcr.io/${{ github.repository_owner }}/goodbye:${{ github.sha }}

      # 4. Deploy to VPS via SSH
      - name: Deploy to VPS
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USERNAME }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            # Navigate to the project directory on the VPS
            cd ~/goodbye

            # Log in to GHCR to pull the image
            echo ${{ secrets.GHCR_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin

            # Pull the latest image
            docker compose pull app

            # Restart with the new image (--force-recreate ensures the
            # container is replaced even if the config hasn't changed)
            docker compose up -d --force-recreate app

            # Clean up old images to save disk space
            docker image prune -f

            # Show running containers (for the CI log)
            docker compose ps
```

**What each step does:**
1. **Checkout** — Downloads your code into the GitHub Actions runner
2. **GHCR Login** — Authenticates so we can push Docker images
3. **Build & Push** — Runs your `Dockerfile`, creates the image, and pushes it to `ghcr.io/yourusername/goodbye:latest`. We also tag it with the commit SHA for rollback capability.
4. **Deploy via SSH** — SSHes into your VPS and pulls + restarts the new image

**Why two tags (`latest` + commit SHA)?** `latest` is always the most recent version — Docker Compose uses this. The SHA tag (`ghcr.io/.../goodbye:abc123`) is an immutable reference to this exact build. If the latest version breaks, you can roll back by changing `docker-compose.yml` to use a specific SHA tag.

### Step 10.5: Prepare the VPS for Deployment

Before the first deployment, you need to set up the project directory on the VPS.

SSH into your VPS:

```bash
ssh mrdesai@YOUR_VPS_IP

# Create the project directory
mkdir -p ~/goodbye
cd ~/goodbye

# Create the docker-compose.yml on the VPS
# (same content as your local one, but with your actual GitHub username)
nano docker-compose.yml
# Paste the docker-compose.yml content from Step 8.3

# Create the .env file with a STRONG production password
nano .env
```

For the production `.env`:

```env
DB_PASSWORD=your_very_strong_password_here_make_it_long_and_random
```

**Generate a strong password:**

```bash
# On your Mac, generate a random password:
openssl rand -base64 24
# Example output: K7x2mP9qR4vN8wL1yH6jT3cA5fB0eD
# Use this as your DB_PASSWORD
```

### Step 10.6: Push and Watch It Deploy

```bash
# On your Mac, in the project directory
git add .
git commit -m "Add CI/CD pipeline"
git push origin main
```

Go to your GitHub repo → **Actions** tab. You should see the workflow running. Click on it to watch the progress. The first build takes 3-5 minutes (downloading Maven dependencies). Subsequent builds are faster because Docker caches the dependency layer.

If the pipeline succeeds, your app is now live at `https://mrdesai.cloud` (though the database is empty — no messages yet).

---

## PHASE 11: Deploying and Loading Your Messages

### Step 11.1: Verify the Deployment

After the GitHub Actions pipeline succeeds:

1. Visit `https://mrdesai.cloud` — you should see the OTP entry page
2. If you see a 502 error, SSH into the VPS and check the logs:

```bash
ssh mrdesai@YOUR_VPS_IP
cd ~/goodbye
docker compose logs -f app
```

Common issues:
- **"Connection refused" to MySQL** — MySQL isn't ready yet. Wait 30 seconds and restart: `docker compose restart app`
- **"Access denied for user 'root'"** — Password mismatch between `.env` and what MySQL was initialized with. If this is the first run, delete the volume and recreate: `docker compose down -v && docker compose up -d`

### Step 11.2: Prepare Your Farewell Messages

**What you're doing:** Creating the SQL script that loads all your personalized messages into the database.

On your Mac, create a file called `messages.sql` in your project root (this won't be committed — it contains personal messages):

```sql
-- Farewell Messages for the Goodbye Website
-- Each friend gets a unique 6-digit OTP

USE goodbye_db;

INSERT INTO farewell_messages (otp, recipient_name, message, opened) VALUES
('482917', 'Rahul', 'Working with you has been one of the highlights of my time here.

Remember that time we debugged that production issue at 2 AM? I think that night cemented our friendship more than any team lunch ever could.

Keep pushing boundaries and never stop asking "but why?" — it is your superpower.

Until our paths cross again!', false),

('739201', 'Priya', 'From day one, you made me feel welcome on the team.

Your attention to detail and your ability to explain complex things simply have taught me so much. I am a better engineer because of you.

I know you will do amazing things. Keep being you!', false),

('615384', 'Amit', 'The team will not be the same without our daily chai breaks and your legendary debugging skills.

Thank you for always having my back during code reviews and for never letting me merge something I would regret.

Stay curious, stay kind.', false);

-- Add more messages following this pattern:
-- ('XXXXXX', 'Name', 'Message text here', false),
```

**Tips for writing messages:**
- Each OTP must be unique — don't reuse codes
- Use newlines (`\n` or actual line breaks in SQL) for paragraph spacing
- Keep OTPs random-looking (don't use `111111`, `123456`, etc.) — your friends might try guessing other people's codes!
- Write all ~50 messages in this file. Take your time — these are going to be meaningful to people.

**Generate random OTPs:**

```bash
# Generate 50 random 6-digit OTPs on your Mac
for i in {1..50}; do
  echo $(( RANDOM % 900000 + 100000 ))
done
```

### Step 11.3: Load Messages into Production Database

SSH into your VPS and load the messages:

```bash
ssh mrdesai@YOUR_VPS_IP
cd ~/goodbye

# Copy the SQL file to the VPS (run this from your Mac, not the VPS)
# Open a new terminal tab on your Mac:
scp /Users/mrdesai/Desktop/goodbye/messages.sql mrdesai@YOUR_VPS_IP:~/goodbye/

# Back on the VPS, load the messages into MySQL
docker exec -i goodbye-mysql mysql -u root -p"$(grep DB_PASSWORD .env | cut -d= -f2)" goodbye_db < messages.sql
```

**What's happening here?**
- `docker exec -i goodbye-mysql` — Run a command inside the MySQL container
- `mysql -u root -p"..."` — Connect to MySQL with the password from `.env`
- `< messages.sql` — Feed the SQL file as input

**Verify the messages were loaded:**

```bash
docker exec -it goodbye-mysql mysql -u root -p"$(grep DB_PASSWORD .env | cut -d= -f2)" goodbye_db -e "SELECT id, otp, recipient_name, opened FROM farewell_messages;"
```

You should see a table of all your friends' names and OTPs.

### Step 11.4: Add messages.sql to .gitignore

Make sure your personal messages don't end up on GitHub:

```bash
# On your Mac
echo "messages.sql" >> .gitignore
git add .gitignore
git commit -m "Exclude messages.sql from repo"
git push origin main
```

---

## PHASE 12: Testing Everything End-to-End

### Step 12.1: The Golden Path Test

On your phone or Mac browser:

1. Go to `https://mrdesai.cloud`
2. You should see the OTP entry page with the dark gradient background
3. Enter one of your OTPs (e.g., `482917`)
4. You should see the farewell page with:
   - "Dear Rahul," as the greeting
   - Your personalized message with proper paragraph spacing
   - "With gratitude, Swapnil" as the sign-off
   - A "Save as PDF" button at the bottom
5. Click "Save as PDF" — the print dialog should open
6. The print preview should show a clean letter without the button

### Step 12.2: Error Path Tests

1. **Wrong OTP:** Enter `000000` — you should see the error message, not a crash
2. **Non-numeric input:** Enter `abcdef` — should show "Please enter a valid 6-digit code"
3. **Empty submit:** Try submitting empty — the browser's HTML validation should catch it
4. **Direct URL:** Visit `https://mrdesai.cloud/farewell/000000` — should redirect to the OTP page
5. **Random paths:** Visit `https://mrdesai.cloud/anything` — should show the custom error page

### Step 12.3: Mobile Test

**This is critical** — most of your friends will open this on their phones.

1. Open `https://mrdesai.cloud` on your phone
2. The numeric keypad should appear (thanks to `inputmode="numeric"`)
3. The OTP input should be easy to type on a small screen
4. The farewell page should be readable without horizontal scrolling
5. "Save as PDF" should work (on iOS: Share → Print → pinch to zoom → Share as PDF)

### Step 12.4: Share the OTPs

Create a list mapping friends to their OTPs. You'll send each person their code privately (WhatsApp, text, etc.):

```
Rahul    → 482917
Priya    → 739201
Amit     → 615384
...
```

**When you're ready to share**, send each friend a message like:

> "Hey! I've got a little something for you at mrdesai.cloud — enter this code: 482917"

### Step 12.5: Monitor Who Opens Their Messages

SSH into the VPS and check:

```bash
ssh mrdesai@YOUR_VPS_IP
cd ~/goodbye

# See who has opened their message
docker exec -it goodbye-mysql mysql -u root -p"$(grep DB_PASSWORD .env | cut -d= -f2)" goodbye_db \
  -e "SELECT recipient_name, opened, opened_at FROM farewell_messages ORDER BY opened DESC, recipient_name;"
```

### Troubleshooting Guide

| Problem | Cause | Fix |
|---------|-------|-----|
| Site shows 502 Bad Gateway | App container isn't running | `cd ~/goodbye && docker compose logs app` to check errors, then `docker compose up -d` |
| "Connection refused" in app logs | MySQL not ready | `docker compose restart app` — wait 30s for MySQL to be healthy |
| OTP not found (but you inserted it) | Wrong database or typo in OTP | Verify: `docker exec -it goodbye-mysql mysql ... -e "SELECT otp FROM farewell_messages;"` |
| HTTPS not working | Certbot certificate issue | Re-run: `sudo certbot --nginx -d mrdesai.cloud -d www.mrdesai.cloud` |
| GitHub Actions fails at SSH step | SSH key mismatch | Verify the public key is in `~/.ssh/authorized_keys` on the VPS |
| GitHub Actions fails at Docker push | GHCR token expired | Generate a new PAT and update the `GHCR_TOKEN` secret |
| Changes not showing after deploy | Browser cache | Hard refresh: `Cmd+Shift+R` (Mac) or `Ctrl+Shift+R` (Windows) |
| MySQL data disappeared | Volume was deleted | Your data lives in the `mysql_data` Docker volume. Only `docker compose down -v` deletes it. Never run `-v` in production unless you mean to wipe the database. |

---

## STATUS: All Phases Complete

After completing all 12 phases, you have:

- [x] VPS running Ubuntu 22.04 with a non-root user
- [x] Docker installed on VPS
- [x] Domain pointing to VPS
- [x] Nginx running as reverse proxy with HTTPS
- [x] Spring Boot project created locally in IntelliJ
- [x] Complete Java application (entity, repository, controller)
- [x] Beautiful Thymeleaf templates with responsive CSS
- [x] Docker multi-stage build + Docker Compose
- [x] GitHub repository with proper .gitignore
- [x] Automated CI/CD pipeline with GitHub Actions
- [x] Farewell messages loaded into production database
- [x] End-to-end testing on desktop and mobile

**Your farewell website is live at `https://mrdesai.cloud`** — go make your friends smile.
