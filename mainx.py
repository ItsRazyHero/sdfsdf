import discord
import random
import asyncio
import requests
from bs4 import BeautifulSoup as BS

# parser connection
r = requests.get("https://randstuff.ru/saying/")
html = BS(r.content, "html.parser")
r1 = requests.get("https://randstuff.ru/fact/")
html1 = BS(r1.content, "html.parser")

# getting text btw it's just an example
for el in html.select('#saying'):
    title = el.select('td')
aboba = str(title)[5:str(title).find('.')+1]

#WorldLevel = 0
#WorldPoints = 0
#def isLevelReached():
#    global WorldLevel, WorldPoints
#    if 10*(WorldLevel+1) <= WorldPoints:
#        WorldLevel += 1
#        WorldPoints = 0

#main body
class MyClient(discord.Client):
    async def on_ready(self):
        print('Залогинился как:')
        print(self.user.name)
        print(self.user.id)
        print('------')

    async def on_message(self, message):
        if message.author.id == self.user.id:
            return
#command that post a citate form random org site 
        if message.content.startswith('+cit'):
            for el in html.select('#saying'):
                title = el.select('td')
            aboba = str(title)[5:str(title).find('.')+1]
            await message.channel.send(aboba)
#command that post a fact from random org site
        if message.content.startswith('+fact'):
            for el in html1.select('#fact'):
                title = el.select('td')
            avtobus = str(title)[5:str(title).find('.')+1]
            await message.channel.send(avtobus)
#dead-inside command
        if message.content.startswith('+1000-7'):
            counter = 1000
            mescon = ''
            while counter > 0:
                mescon = str(counter - 7)
                output = str(counter)
                output += '-7='
                await message.channel.send(str(output)+mescon)
                counter -= 7
        if message.content.startswith('+x'):
            await message.channel.send('!store')
#new 8ball command
        if message.content.startswith('+8ball'):
            await message.channel.send(answers[random.randint(0, len(answers)-1)])
#other commands
        if message.content.startswith('+привет'):
            await message.reply('Привет!', mention_author=True)

        if message.content.startswith('+загадай'):
            await message.channel.send('Я загадал число от 1 до 10. Угадай его')

            def is_correct(m):
                return m.author == message.author and m.content.isdigit()

            answer = random.randint(1, 10)

            try:
                guess = await self.wait_for('message', check=is_correct, timeout=5.0)
            except asyncio.TimeoutError:
                return await message.channel.send('Ой, ты слишком много думаешь :P Ответ: {}.'.format(answer))

            if int(guess.content) == answer:
                await message.channel.send('Угадал! Ну даёшь!')
            else:
                await message.channel.send('Не-а, мимо. Ответ: {}.'.format(answer))
    async def on_reaction_add(reaction, user):
        print(user, 'just reacted with', reaction)


client = MyClient()
client.run('Your Token There')
