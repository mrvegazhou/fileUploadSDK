package main

import (
	"errors"
	"fmt"
	"github.com/robfig/cron"
	"io"
	"log"
	"os"
	"path"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

var (
	CleanCount int64
	StartTime  time.Time
	Info       *log.Logger
	Error      *log.Logger
)

type Config struct {
	dir            string
	urlPrefix      string
	localPrefix    string
	expirationTime int64
	checkDirTime   int64
}

func handleRecursive(root string, config *Config) error {
	var w sync.WaitGroup
	if root == "" {
		return errors.New("not dir")
	}

	file, err := os.Open(root)
	if err != nil {
		return err
	}

	defer func() {
		file.Close()
	}()

	files, err := file.Readdir(0)
	if err != nil {
		return nil
	}

	for _, info := range files {
		if info.IsDir() {
			diff := time.Now().Unix() - info.ModTime().Unix()
			if diff < config.checkDirTime {
				w.Add(1)
				go func(name string, config *Config) {
					defer w.Done()
					path := path.Join(root, name)
					handleRecursive(path, config)
					matched := checkMatched(name, config)
					if matched && checkExpired(path, config) {
						Info.Println(path)
						handleRemove(root, name)
					}
				}(info.Name(), config)
			}
		}
	}
	w.Wait()
	return err
}

func checkExpired(path string, config *Config) bool {
	f, err := os.Open(path)
	if err != nil {
		Error.Println(err)
		return false
	}
	defer f.Close()
	fi, err := f.Stat()
	if err != nil {
		Error.Println(err)
		return false
	}
	diff := time.Now().Unix() - fi.ModTime().Unix()
	if diff > config.expirationTime {
		return true
	}
	return false
}

func checkMatched(name string, config *Config) bool {
	for _, prefix := range []string{config.localPrefix, config.urlPrefix} {
		if strings.HasPrefix(name, prefix) {
			return true
		}
	}
	return false
}

func handleRemove(root string, name string) error {
	if err := os.RemoveAll(path.Join(root, name)); err != nil {
		return err
	}
	atomic.AddInt64(&CleanCount, 1)
	Info.Println("\r用时：%dms, 删除总数：%d \t", time.Duration(time.Since(StartTime).Nanoseconds())/time.Millisecond, CleanCount)
	return nil
}

func init() {
	infoFile, err := os.OpenFile("infos.log", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil {
		log.Fatalln("打开日志文件失败：", err)
	}
	Info = log.New(io.MultiWriter(os.Stderr, infoFile), "Info:", log.Ldate|log.Ltime|log.Lshortfile)
	Error = log.New(io.MultiWriter(os.Stderr, infoFile), "Error:", log.Ldate|log.Ltime|log.Lshortfile)
}

func handleInit() (*Config, error) {
	Error.Println("ssdfdsfdf")
	config := &Config{
		dir:            "D:/workspace/gitspace/spring-boot-upload-file/longxin/", //"/nfs/sharetest/",
		urlPrefix:      "_#",
		localPrefix:    "_#temp",
		expirationTime: 172800,  //604800, 当临时文件的修改时间大于7天后进行删除操作
		checkDirTime:   1209600, //如果文件夹大于14天就无需进行检测
	}
	fi, err := os.Stat(config.dir)
	if err != nil {
		Error.Println(err)
		return nil, err
	}
	if fi == nil {
		Error.Println(err)
		return nil, err
	}
	if !fi.IsDir() {
		Error.Println(err)
		return nil, errors.New("not dir")
	}
	return config, nil
}

func main() {
	c := cron.New()
	spec := "0 32 16 * * *" //0 0 1 1#2,1#4 * ?  每个月的第二个和第四个周日凌晨1点
	c.AddFunc(spec, func() {
		config, err := handleInit()
		if err == nil {
			fmt.Println(config.dir)
			handleRecursive(config.dir, config)
		}
	})
	c.Start()
	select {}
}
